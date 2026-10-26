package net.ronnylearn.bedprotect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class BlastPredictor {
	public static final float BED_POWER = 5.0F;
	private static final float MAX_RAY_FACTOR = 1.3F;
	private static final EquipmentSlot[] ARMOR_SLOTS = {
		EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD
	};
	private static final double EXPLOSIVE_ENTITY_RADIUS = 12.0;
	private static final int LAVA_REACH = 4;
	private static final int MAX_FLIGHT_TICKS = 400;

	private BlastPredictor() {
	}

	public static @Nullable BlastAssessment assess(Player player, BlockPos clickedPos, BlockState state) {
		Level level = player.level();
		BlockPos[] halves = bedHalves(level, clickedPos, state);
		if (halves == null) {
			return null;
		}
		BlockPos head = halves[0];
		BlockPos foot = halves[1];
		BedBlock bed = (BedBlock) state.getBlock();
		if (!bedsBreakHere(level, bed, head)) {
			return null;
		}

		Vec3 center = Vec3.atCenterOf(head);
		DamageSource source = level.damageSources().badRespawnPointExplosion(center);
		float health = PingGuard.health(player);
		float absorption = PingGuard.absorption(player);
		boolean invulnerable = player.getAbilities().invulnerable && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
		double distance = Math.sqrt(player.distanceToSqr(center));

		float doubleRadius = BED_POWER * 2.0F;
		double relativeDistance = distance / (double) doubleRadius;
		float exposure = 0.0F;
		float scaled = 0.0F;
		if (relativeDistance <= 1.0) {
			exposure = seenPercent(level, center, player, head, foot);
			double pow = (1.0 - relativeDistance) * (double) exposure;
			scaled = (float) ((pow * pow + pow) / 2.0 * 7.0 * (double) doubleRadius + 1.0);
			scaled = scaleWithDifficulty(source, level.getDifficulty(), scaled);
		}
		if (invulnerable) {
			scaled = 0.0F;
		}

		boolean[] breaks = new boolean[ARMOR_SLOTS.length];
		int broken = 0;
		if (scaled > 0.0F && !source.is(DamageTypeTags.BYPASSES_ARMOR)) {
			int durabilityDamage = (int) Math.max(1.0F, scaled / 4.0F);
			for (int i = 0; i < ARMOR_SLOTS.length; i++) {
				breaks[i] = breaksFrom(player.getItemBySlot(ARMOR_SLOTS[i]), durabilityDamage, source);
				if (breaks[i]) {
					broken++;
				}
			}
		}
		Defense blast = defend(player, source, scaled, breaks);

		Set<BlastAssessment.Hazard> hazards = EnumSet.noneOf(BlastAssessment.Hazard.class);
		double knockback = 0.0;
		double fallDistance = 0.0;
		float fallDamage = 0.0F;
		if (!invulnerable) {
			boolean fireproof = player.hasEffect(MobEffects.FIRE_RESISTANCE);
			if (player.isInLava() && !fireproof) {
				hazards.add(BlastAssessment.Hazard.IN_LAVA);
			}
			if (player.isOnFire() && !fireproof) {
				hazards.add(BlastAssessment.Hazard.BURNING);
			}
			if (player.isFallFlying()) {
				hazards.add(BlastAssessment.Hazard.GLIDING);
			}
			Set<BlockPos> crater = worstCaseCrater(level, center, head, foot);
			if (explosivesNear(level, center, crater)) {
				hazards.add(BlastAssessment.Hazard.EXPLOSIVES);
			}
			if (!fireproof && lavaCanReach(level, player, crater)) {
				hazards.add(BlastAssessment.Hazard.LAVA_NEARBY);
			}

			Vec3 push = Vec3.ZERO;
			if (relativeDistance <= 1.0 && (!player.getAbilities().flying)) {
				double power = (1.0 - relativeDistance) * (double) exposure * (1.0 - knockbackResistance(player));
				push = player.getEyePosition().subtract(center).normalize().scale(power);
			}
			knockback = push.length();
			Flight flight = fly(level, player, player.getDeltaMovement().add(push), crater, head, foot);
			fallDistance = flight.fallDistance();
			if (flight.lava() && !fireproof) {
				hazards.add(BlastAssessment.Hazard.LAVA_NEARBY);
			}
			if (flight.landed() == null || flight.lava() && !fireproof) {
				hazards.add(BlastAssessment.Hazard.DEADLY_DROP);
			} else {
				fallDamage = fallDamage(player, flight, breaks);
			}
		}

		return new BlastAssessment(head, true, invulnerable, distance, exposure, scaled, blast.afterArmor(),
			blast.afterEffects(), blast.protection(), broken, blast.damage(), knockback, fallDistance, fallDamage,
			health, absorption, hazards);
	}

	@Nullable
	static BlockPos[] bedHalves(Level level, BlockPos pos, BlockState state) {
		if (!(state.getBlock() instanceof BedBlock)) {
			return null;
		}
		BedPart part = state.getValue(BedBlock.PART);
		Direction facing = state.getValue(BedBlock.FACING);
		BlockPos otherPos = pos.relative(part == BedPart.FOOT ? facing : facing.getOpposite());
		BlockState other = level.getBlockState(otherPos);
		if (!other.is(state.getBlock()) || other.getValue(BedBlock.PART) == part) {
			return null;
		}
		return part == BedPart.HEAD ? new BlockPos[]{pos, otherPos} : new BlockPos[]{otherPos, pos};
	}

	public static boolean bedsBreakHere(Level level, BedBlock bed, BlockPos pos) {
		if (level.dimension() == Level.NETHER || level.dimension() == Level.END) {
			return true;
		}
		try {
			return !BedBlock.canSetSpawn(level);
		} catch (RuntimeException e) {
			return false;
		}
	}

	private record Defense(float afterArmor, float afterEffects, float protection, float damage) {
	}

	private static Defense defend(Player player, DamageSource source, float damage, boolean[] brokenArmor) {
		if (damage <= 0.0F) {
			return new Defense(0.0F, 0.0F, 0.0F, 0.0F);
		}
		float afterArmor = damage;
		if (!source.is(DamageTypeTags.BYPASSES_ARMOR)) {
			double armor = player.getAttributeValue(Attributes.ARMOR);
			double toughness = player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
			afterArmor = CombatRules.getDamageAfterAbsorb(player, damage, source,
				(float) Mth.floor(Math.max(armor, 0.0)), (float) Math.max(toughness, 0.0));
		}
		if (source.is(DamageTypeTags.BYPASSES_EFFECTS)) {
			return new Defense(afterArmor, afterArmor, 0.0F, afterArmor);
		}
		float afterEffects = afterArmor;
		MobEffectInstance resistance = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
		if (resistance != null && !source.is(DamageTypeTags.BYPASSES_RESISTANCE)) {
			int absorb = 25 - (resistance.getAmplifier() + 1) * 5;
			afterEffects = Math.max(afterArmor * (float) absorb / 25.0F, 0.0F);
		}
		if (afterEffects <= 0.0F) {
			return new Defense(afterArmor, 0.0F, 0.0F, 0.0F);
		}
		if (source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) {
			return new Defense(afterArmor, afterEffects, 0.0F, afterEffects);
		}
		float protection = protectionPoints(player, source, brokenArmor);
		float result = protection > 0.0F ? CombatRules.getDamageAfterMagicAbsorb(afterEffects, protection) : afterEffects;
		return new Defense(afterArmor, afterEffects, protection, result);
	}

	private static float scaleWithDifficulty(DamageSource source, Difficulty difficulty, float damage) {
		if (!source.scalesWithDifficulty()) {
			return damage;
		}
		return switch (difficulty) {
			case PEACEFUL -> 0.0F;
			case EASY -> Math.min(damage / 2.0F + 1.0F, damage);
			case HARD -> damage * 3.0F / 2.0F;
			default -> damage;
		};
	}

	private static boolean breaksFrom(ItemStack stack, int durabilityDamage, DamageSource source) {
		if (stack.isEmpty()) {
			return false;
		}
		return stack.getItem() instanceof ArmorItem && stack.isDamageableItem() && stack.canBeHurtBy(source)
			&& stack.getDamageValue() + durabilityDamage >= stack.getMaxDamage();
	}

	private static float protectionPoints(Player player, DamageSource source, boolean[] broken) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return 0.0F;
		}
		boolean explosion = source.is(DamageTypeTags.IS_EXPLOSION);
		boolean fall = source.is(DamageTypeTags.IS_FALL);
		boolean fire = source.is(DamageTypeTags.IS_FIRE);
		float points = 0.0F;
		for (int i = 0; i < ARMOR_SLOTS.length; i++) {
			ItemStack stack = player.getItemBySlot(ARMOR_SLOTS[i]);
			if (stack.isEmpty() || broken[i]) {
				continue;
			}
			points += level(player, Enchantments.PROTECTION, stack);
			if (explosion) {
				points += 2.0F * level(player, Enchantments.BLAST_PROTECTION, stack);
			}
			if (fall) {
				points += 3.0F * level(player, Enchantments.FEATHER_FALLING, stack);
			}
			if (fire) {
				points += 2.0F * level(player, Enchantments.FIRE_PROTECTION, stack);
			}
		}
		return points;
	}

	private static int level(Player player, ResourceKey<Enchantment> key, ItemStack stack) {
		Optional<? extends Holder<Enchantment>> holder = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key);
		return holder.map(h -> EnchantmentHelper.getItemEnchantmentLevel(h, stack)).orElse(0);
	}

	private static double knockbackResistance(Player player) {
		double fromEnchantments = 0.0;
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			fromEnchantments += 0.15 * level(player, Enchantments.BLAST_PROTECTION, player.getItemBySlot(slot));
		}
		double attribute = player.getAttributeValue(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE);
		return Mth.clamp(Math.max(attribute, fromEnchantments), 0.0, 1.0);
	}

	static float seenPercent(Level level, Vec3 center, Entity entity, BlockPos head, BlockPos foot) {
		AABB bb = entity.getBoundingBox();
		double xs = 1.0 / ((bb.maxX - bb.minX) * 2.0 + 1.0);
		double ys = 1.0 / ((bb.maxY - bb.minY) * 2.0 + 1.0);
		double zs = 1.0 / ((bb.maxZ - bb.minZ) * 2.0 + 1.0);
		double xOffset = (1.0 - Math.floor(1.0 / xs) * xs) / 2.0;
		double zOffset = (1.0 - Math.floor(1.0 / zs) * zs) / 2.0;
		if (xs < 0.0 || ys < 0.0 || zs < 0.0) {
			return 0.0F;
		}
		CollisionContext context = CollisionContext.of(entity);
		int hits = 0;
		int count = 0;
		for (double xx = 0.0; xx <= 1.0; xx += xs) {
			for (double yy = 0.0; yy <= 1.0; yy += ys) {
				for (double zz = 0.0; zz <= 1.0; zz += zs) {
					double x = Mth.lerp(xx, bb.minX, bb.maxX);
					double y = Mth.lerp(yy, bb.minY, bb.maxY);
					double z = Mth.lerp(zz, bb.minZ, bb.maxZ);
					Vec3 from = new Vec3(x + xOffset, y, z + zOffset);
					if (!blocked(level, from, center, context, head, foot)) {
						hits++;
					}
					count++;
				}
			}
		}
		return (float) hits / (float) count;
	}

	private static boolean blocked(Level level, Vec3 from, Vec3 to, CollisionContext context, BlockPos head, BlockPos foot) {
		return BlockGetter.traverseBlocks(from, to, context, (ctx, pos) -> {
			if (pos.equals(head) || pos.equals(foot)) {
				return null;
			}
			VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos, ctx);
			return shape.clip(from, to, pos) != null ? Boolean.TRUE : null;
		}, ctx -> Boolean.FALSE);
	}

	static Set<BlockPos> worstCaseCrater(Level level, Vec3 center, BlockPos head, BlockPos foot) {
		Set<BlockPos> blown = new HashSet<>();
		for (int xx = 0; xx < 16; xx++) {
			for (int yy = 0; yy < 16; yy++) {
				for (int zz = 0; zz < 16; zz++) {
					if (xx != 0 && xx != 15 && yy != 0 && yy != 15 && zz != 0 && zz != 15) {
						continue;
					}
					double xd = (double) ((float) xx / 15.0F * 2.0F - 1.0F);
					double yd = (double) ((float) yy / 15.0F * 2.0F - 1.0F);
					double zd = (double) ((float) zz / 15.0F * 2.0F - 1.0F);
					double d = Math.sqrt(xd * xd + yd * yd + zd * zd);
					xd /= d;
					yd /= d;
					zd /= d;
					float remaining = BED_POWER * MAX_RAY_FACTOR;
					double xp = center.x;
					double yp = center.y;
					double zp = center.z;
					for (; remaining > 0.0F; remaining -= 0.22500001F) {
						BlockPos pos = BlockPos.containing(xp, yp, zp);
						if (!level.isInWorldBounds(pos)) {
							break;
						}
						if (!pos.equals(head) && !pos.equals(foot)) {
							BlockState block = level.getBlockState(pos);
							FluidState fluid = level.getFluidState(pos);
							if (!block.isAir() || !fluid.isEmpty()) {
								float resistance = Math.max(block.getBlock().getExplosionResistance(), fluid.getExplosionResistance());
								remaining -= (resistance + 0.3F) * 0.3F;
							}
						}
						if (remaining > 0.0F) {
							blown.add(pos);
						}
						xp += xd * 0.3F;
						yp += yd * 0.3F;
						zp += zd * 0.3F;
					}
				}
			}
		}
		return blown;
	}

	private static boolean explosivesNear(Level level, Vec3 center, Set<BlockPos> crater) {
		for (BlockPos pos : crater) {
			if (level.getBlockState(pos).is(Blocks.TNT)) {
				return true;
			}
		}
		AABB area = new AABB(center, center).inflate(EXPLOSIVE_ENTITY_RADIUS);
		return !level.getEntities((Entity) null, area, entity -> entity.getType() == EntityType.TNT
			|| entity.getType() == EntityType.TNT_MINECART || entity.getType() == EntityType.END_CRYSTAL).isEmpty();
	}

	private static boolean lavaCanReach(Level level, Player player, Set<BlockPos> crater) {
		AABB box = player.getBoundingBox();
		for (BlockPos pos : BlockPos.betweenClosed(
			Mth.floor(box.minX - 1.0), Mth.floor(box.minY) - 1, Mth.floor(box.minZ - 1.0),
			Mth.floor(box.maxX + 1.0), Mth.floor(box.maxY), Mth.floor(box.maxZ + 1.0))) {
			if (level.getFluidState(pos).is(FluidTags.LAVA)) {
				return true;
			}
		}
		BlockPos feet = player.blockPosition();
		for (BlockPos pos : crater) {
			if (Math.abs(pos.getX() - feet.getX()) > LAVA_REACH || Math.abs(pos.getZ() - feet.getZ()) > LAVA_REACH
				|| pos.getY() < feet.getY() - 2 || pos.getY() > feet.getY() + 3) {
				continue;
			}
			for (Direction direction : Direction.values()) {
				BlockPos next = pos.relative(direction);
				if (!crater.contains(next) && level.getFluidState(next).is(FluidTags.LAVA)) {
					return true;
				}
			}
		}
		return false;
	}

	record Flight(double fallDistance, @Nullable BlockState landed, boolean water, boolean lava) {
	}

	static Flight fly(Level level, Player player, Vec3 velocity, Set<BlockPos> crater, BlockPos head, BlockPos foot) {
		AABB box = player.getBoundingBox();
		CollisionContext context = CollisionContext.of(player);
		double gravity = player.getAttributeValue(Attributes.GRAVITY);
		boolean slowFalling = player.hasEffect(MobEffects.SLOW_FALLING);
		double vx = velocity.x;
		double vy = velocity.y;
		double vz = velocity.z;
		double fall = player.fallDistance;
		boolean lava = false;
		for (int tick = 0; tick < MAX_FLIGHT_TICKS; tick++) {
			List<VoxelShape> shapes = shapesAround(level, box.expandTowards(vx, vy, vz), context, crater, head, foot);
			double dy = Shapes.collide(Direction.Axis.Y, box, shapes, vy);
			box = box.move(0.0, dy, 0.0);
			double dx;
			double dz;
			if (Math.abs(vx) < Math.abs(vz)) {
				dz = Shapes.collide(Direction.Axis.Z, box, shapes, vz);
				box = box.move(0.0, 0.0, dz);
				dx = Shapes.collide(Direction.Axis.X, box, shapes, vx);
				box = box.move(dx, 0.0, 0.0);
			} else {
				dx = Shapes.collide(Direction.Axis.X, box, shapes, vx);
				box = box.move(dx, 0.0, 0.0);
				dz = Shapes.collide(Direction.Axis.Z, box, shapes, vz);
				box = box.move(0.0, 0.0, dz);
			}
			if (touches(level, box, FluidTags.LAVA)) {
				lava = true;
			}
			if (touches(level, box, FluidTags.WATER)) {
				return new Flight(0.0, Blocks.WATER.defaultBlockState(), true, lava);
			}
			if (dy < 0.0) {
				fall -= dy;
			}
			if (vy < 0.0 && dy != vy) {
				BlockPos below = BlockPos.containing(box.getCenter().x, box.minY - 0.2, box.getCenter().z);
				return new Flight(fall, level.getBlockState(below), false, lava);
			}
			if (box.maxY < level.getMinBuildHeight() - 64) {
				return new Flight(fall, null, false, lava);
			}
			if (dx != vx) {
				vx = 0.0;
			}
			if (dz != vz) {
				vz = 0.0;
			}
			if (dy != vy) {
				vy = 0.0;
			}
			double g = vy <= 0.0 && slowFalling ? Math.min(gravity, 0.01) : gravity;
			vx *= 0.91F;
			vy = (vy - g) * 0.98F;
			vz *= 0.91F;
		}
		return new Flight(fall, null, false, lava);
	}

	private static List<VoxelShape> shapesAround(Level level, AABB area, CollisionContext context, Set<BlockPos> crater,
		BlockPos head, BlockPos foot) {
		List<VoxelShape> shapes = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(
			Mth.floor(area.minX - 1.0E-7), Mth.floor(area.minY - 1.0E-7) - 1, Mth.floor(area.minZ - 1.0E-7),
			Mth.floor(area.maxX + 1.0E-7), Mth.floor(area.maxY + 1.0E-7), Mth.floor(area.maxZ + 1.0E-7))) {
			if (crater.contains(pos) || pos.equals(head) || pos.equals(foot)) {
				continue;
			}
			VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos, context);
			if (!shape.isEmpty()) {
				shapes.add(shape.move(pos.getX(), pos.getY(), pos.getZ()));
			}
		}
		return shapes;
	}

	private static boolean touches(Level level, AABB box, TagKey<Fluid> fluid) {
		AABB inner = box.deflate(0.001);
		for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(inner.minX), Mth.floor(inner.minY), Mth.floor(inner.minZ),
			Mth.floor(inner.maxX), Mth.floor(inner.maxY), Mth.floor(inner.maxZ))) {
			if (level.getFluidState(pos).is(fluid)) {
				return true;
			}
		}
		return false;
	}

	private static float fallDamage(Player player, Flight flight, boolean[] brokenArmor) {
		if (flight.water() || flight.landed() == null || player.hasEffect(MobEffects.SLOW_FALLING)) {
			return 0.0F;
		}
		double distance = flight.fallDistance();
		float modifier = 1.0F;
		BlockState landed = flight.landed();
		if (landed.is(Blocks.HAY_BLOCK) || landed.is(Blocks.HONEY_BLOCK)) {
			modifier = 0.2F;
		} else if (landed.is(BlockTags.BEDS)) {
			modifier = 0.5F;
		} else if (landed.is(Blocks.POINTED_DRIPSTONE)) {
			distance += 2.0;
			modifier = 2.0F;
		}
		float power = (float) distance - (float) player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
		int damage = Mth.ceil((double) (power * modifier) * player.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER));
		if (damage <= 0) {
			return 0.0F;
		}
		DamageSource source = player.level().damageSources().fall();
		float scaled = scaleWithDifficulty(source, player.level().getDifficulty(), (float) damage);
		return defend(player, source, scaled, brokenArmor).damage();
	}
}
