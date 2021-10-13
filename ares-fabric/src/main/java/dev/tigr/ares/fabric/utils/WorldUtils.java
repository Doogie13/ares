package dev.tigr.ares.fabric.utils;

import com.google.common.collect.Streams;
import dev.tigr.ares.Wrapper;
import dev.tigr.ares.core.feature.FriendManager;
import dev.tigr.ares.core.util.Pair;
import dev.tigr.ares.fabric.impl.modules.player.Freecam;
import dev.tigr.ares.fabric.mixin.accessors.MinecraftClientAccessor;
import dev.tigr.ares.fabric.mixin.accessors.RenderTickCounterAccessor;
import io.netty.buffer.Unpooled;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.AmbientEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.SquidEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static dev.tigr.ares.fabric.impl.modules.player.RotationManager.ROTATIONS;

/**
 * @author Tigermouthbear 9/26/20
 */
public class WorldUtils implements Wrapper {
    public static PlayerEntity getPlayer() {
        if(Freecam.INSTANCE.getEnabled()) return Freecam.INSTANCE.clone;
        return MC.player;
    }

    public static boolean placeBlockMainHand(BlockPos pos) {
        return placeBlockMainHand(false, -1, -1, false, false, pos);
    }
    public static boolean placeBlockMainHand(Boolean rotate, int rotationKey, int rotationPriority, boolean instantRotation, boolean instantBypassesCurrent, BlockPos pos) {
        return placeBlockMainHand(rotate, rotationKey, rotationPriority, instantRotation, instantBypassesCurrent, pos, true);
    }
    public static boolean placeBlockMainHand(Boolean rotate, int rotationKey, int rotationPriority, boolean instantRotation, boolean instantBypassesCurrent, BlockPos pos, Boolean airPlace) {
        return placeBlockMainHand(rotate, rotationKey, rotationPriority, instantRotation, instantBypassesCurrent, pos, airPlace, false);
    }
    public static boolean placeBlockMainHand(Boolean rotate, int rotationKey, int rotationPriority, boolean instantRotation, boolean instantBypassesCurrent, BlockPos pos, Boolean airPlace, Boolean ignoreEntity) {
        return placeBlockMainHand(rotate, rotationKey, rotationPriority, instantRotation, instantBypassesCurrent, pos, airPlace, ignoreEntity, null);
    }
    public static boolean placeBlockMainHand(Boolean rotate, int rotationKey, int rotationPriority, boolean instantRotation, boolean instantBypassesCurrent, BlockPos pos, Boolean airPlace, Boolean ignoreEntity, Direction overrideSide) {
        return placeBlock(rotate, rotationKey, rotationPriority, instantRotation, instantBypassesCurrent, Hand.MAIN_HAND, pos, airPlace, ignoreEntity, overrideSide);
    }
    public static boolean placeBlockNoRotate(Hand hand, BlockPos pos) {
        return placeBlock(false, -1, -1, false, false, hand, pos, true, false);
    }

    public static boolean placeBlock(Hand hand, BlockPos pos) {
        placeBlock(false, -1, -1, false, false, hand, pos, true);
        return true;
    }
    public static boolean placeBlock(Boolean rotate, int rotationKey, int rotationPriority, boolean instantRotation, boolean instantBypassesCurrent, Hand hand, BlockPos pos) {
        placeBlock(rotate, rotationKey, rotationPriority, instantRotation, instantBypassesCurrent, hand, pos, false);
        return true;
    }
    public static boolean placeBlock(Boolean rotate, int rotationKey, int rotationPriority, boolean instantRotation, boolean instantBypassesCurrent, Hand hand, BlockPos pos, Boolean airPlace) {
        placeBlock(rotate, rotationKey, rotationPriority, instantRotation, instantBypassesCurrent, hand, pos, airPlace, false);
        return true;
    }
    public static boolean placeBlock(Boolean rotate, int rotationKey, int rotationPriority, boolean instantRotation, boolean instantBypassesCurrent, Hand hand, BlockPos pos, Boolean airPlace, Boolean ignoreEntity) {
        placeBlock(rotate, rotationKey, rotationPriority, instantRotation, instantBypassesCurrent, hand, pos, airPlace, ignoreEntity, null);
        return true;
    }

    public static boolean placeBlock(Boolean rotate, int rotationKey, int rotationPriority, boolean instantRotation, boolean instantBypassesCurrent, Hand hand, BlockPos pos, Boolean airPlace, Boolean ignoreEntity, Direction overrideSide) {
        // make sure place is empty if ignoreEntity is not true
        if(ignoreEntity) {
            if (!MC.world.getBlockState(pos).getMaterial().isReplaceable())
                return false;
        } else if(!MC.world.getBlockState(pos).getMaterial().isReplaceable() || !MC.world.canPlace(Blocks.OBSIDIAN.getDefaultState(), pos, ShapeContext.absent()))
            return false;

        Vec3d eyesPos = new Vec3d(MC.player.getX(),
                MC.player.getY() + MC.player.getEyeHeight(MC.player.getPose()),
                MC.player.getZ());

        Vec3d hitVec = null;
        BlockPos neighbor = null;
        Direction side2 = null;

        if(overrideSide != null) {
            neighbor = pos.offset(overrideSide.getOpposite());
            side2 = overrideSide;
        }

        for(Direction side: Direction.values()) {
            if(overrideSide == null) {
                neighbor = pos.offset(side);
                side2 = side.getOpposite();

                // check if neighbor can be right clicked aka it isnt air
                if(MC.world.getBlockState(neighbor).isAir() || MC.world.getBlockState(neighbor).getBlock() instanceof FluidBlock) {
                    neighbor = null;
                    side2 = null;
                    continue;
                }
            }

            hitVec = new Vec3d(neighbor.getX(), neighbor.getY(), neighbor.getZ()).add(0.5, 0.5, 0.5).add(new Vec3d(side2.getUnitVector()).multiply(0.5));
            break;
        }

        // Air place if no neighbour was found
        if(airPlace) {
            if (hitVec == null) hitVec = new Vec3d(pos.getX(), pos.getY(), pos.getZ());
            if (neighbor == null) neighbor = pos;
            if (side2 == null) side2 = Direction.UP;
        } else if(hitVec == null || neighbor == null || side2 == null) {
            return false;
        }

        // place block
        double diffX = hitVec.x - eyesPos.x;
        double diffY = hitVec.y - eyesPos.y;
        double diffZ = hitVec.z - eyesPos.z;

        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90F;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));

        float[] rotations = {
                MC.player.getYaw()
                        + MathHelper.wrapDegrees(yaw - MC.player.getYaw()),
                MC.player.getPitch() + MathHelper
                        .wrapDegrees(pitch - MC.player.getPitch())};

        // Rotate using rotation manager and specified settings
        if(rotate)
            if(!ROTATIONS.setCurrentRotation(new Vec2f((float)rotations[0], (float)rotations[1]), rotationKey, rotationPriority, instantRotation, instantBypassesCurrent))
                return false;

        MC.player.networkHandler.sendPacket(new ClientCommandC2SPacket(MC.player, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));
        MC.interactionManager.interactBlock(MC.player, MC.world, hand, new BlockHitResult(hitVec, side2, neighbor, false));
        MC.player.swingHand(hand);
        MC.player.networkHandler.sendPacket(new ClientCommandC2SPacket(MC.player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));

        return true;
    }

    public static final List<Block> NONSOLID_BLOCKS = Arrays.asList(
            Blocks.AIR, Blocks.LAVA, Blocks.WATER, Blocks.GRASS,
            Blocks.VINE, Blocks.SEAGRASS, Blocks.TALL_SEAGRASS,
            Blocks.SNOW, Blocks.TALL_GRASS, Blocks.FIRE, Blocks.VOID_AIR);

    public static boolean canReplace(BlockPos pos) {
        return NONSOLID_BLOCKS.contains(MC.world.getBlockState(pos).getBlock()) && MC.world.getOtherEntities(null, new Box(pos)).stream().noneMatch(Entity::collides);
    }

    //gs code, prob in osiris idk - Doogie
    public static double[] forward(final double speed) {
        float forward = MC.player.input.movementForward;
        float side = MC.player.input.movementSideways;
        float yaw = MC.player.prevYaw + (MC.player.getYaw() - MC.player.prevYaw) * ((RenderTickCounterAccessor) ((MinecraftClientAccessor) MC).getRenderTickCounter()).getTickTime();
        if (forward != 0.0f) {
            if (side > 0.0f) {
                yaw += ((forward > 0.0f) ? -45 : 45);
            } else if (side < 0.0f) {
                yaw += ((forward > 0.0f) ? 45 : -45);
            }
            side = 0.0f;
            if (forward > 0.0f) {
                forward = 1.0f;
            } else if (forward < 0.0f) {
                forward = -1.0f;
            }
        }
        final double sin = Math.sin(Math.toRadians(yaw + 90.0f));
        final double cos = Math.cos(Math.toRadians(yaw + 90.0f));
        final double posX = forward * speed * cos + side * speed * sin;
        final double posZ = forward * speed * sin - side * speed * cos;
        return new double[]{posX, posZ};
    }

    public static double[] forward(final double speed, float strafe, float yaw) {
        float forward = MC.player.input.movementForward;
        float side = strafe;
        if (forward != 0.0f) {
            if (side > 0.0f) {
                yaw += ((forward > 0.0f) ? -45 : 45);
            } else if (side < 0.0f) {
                yaw += ((forward > 0.0f) ? 45 : -45);
            }
            side = 0.0f;
            if (forward > 0.0f) {
                forward = 1.0f;
            } else if (forward < 0.0f) {
                forward = -1.0f;
            }
        }
        final double sin = Math.sin(Math.toRadians(yaw + 90.0f));
        final double cos = Math.cos(Math.toRadians(yaw + 90.0f));
        final double posX = forward * speed * cos + side * speed * sin;
        final double posZ = forward * speed * sin - side * speed * cos;
        return new double[]{posX, posZ};
    }
    // /Doogie

    public static void moveEntityWithSpeed(Entity entity, double speed, boolean shouldMoveY) {
        float yaw = (float) Math.toRadians(MC.player.getYaw());

        double motionX = 0;
        double motionY = 0;
        double motionZ = 0;

        if(MC.player.input.pressingForward) {
            motionX = -(MathHelper.sin(yaw) * speed);
            motionZ = MathHelper.cos(yaw) * speed;
        } else if(MC.player.input.pressingBack) {
            motionX = MathHelper.sin(yaw) * speed;
            motionZ = -(MathHelper.cos(yaw) * speed);
        }

        if(MC.player.input.pressingLeft) {
            motionZ = MathHelper.sin(yaw) * speed;
            motionX = MathHelper.cos(yaw) * speed;
        } else if(MC.player.input.pressingRight) {
            motionZ = -(MathHelper.sin(yaw) * speed);
            motionX = -(MathHelper.cos(yaw) * speed);
        }

        if(shouldMoveY) {
            if(MC.player.input.jumping) {
                motionY = speed;
            } else if(MC.player.input.sneaking) {
                motionY = -speed;
            }
        }

        //strafe
        if(MC.player.input.pressingForward && MC.player.input.pressingLeft) {
            motionX = (MathHelper.cos(yaw) * speed) - (MathHelper.sin(yaw) * speed);
            motionZ = (MathHelper.cos(yaw) * speed) + (MathHelper.sin(yaw) * speed);
        } else if(MC.player.input.pressingLeft && MC.player.input.pressingBack) {
            motionX = (MathHelper.cos(yaw) * speed) + (MathHelper.sin(yaw) * speed);
            motionZ = -(MathHelper.cos(yaw) * speed) + (MathHelper.sin(yaw) * speed);
        } else if(MC.player.input.pressingBack && MC.player.input.pressingRight) {
            motionX = -(MathHelper.cos(yaw) * speed) + (MathHelper.sin(yaw) * speed);
            motionZ = -(MathHelper.cos(yaw) * speed) - (MathHelper.sin(yaw) * speed);
        } else if(MC.player.input.pressingRight && MC.player.input.pressingForward) {
            motionX = -(MathHelper.cos(yaw) * speed) - (MathHelper.sin(yaw) * speed);
            motionZ = (MathHelper.cos(yaw) * speed) - (MathHelper.sin(yaw) * speed);
        }

        entity.setVelocity(motionX, motionY, motionZ);
    }

    public static HoleType isHole(BlockPos pos) {
        BlockState[] blockStates = new BlockState[]{
                MC.world.getBlockState(pos),
                MC.world.getBlockState(pos.add(0, 1, 0)),
                MC.world.getBlockState(pos.add(0, 2, 0)),
                MC.world.getBlockState(pos.add(0, -1, 0)),
                MC.world.getBlockState(pos.add(1, 0, 0)),
                MC.world.getBlockState(pos.add(0, 0, 1)),
                MC.world.getBlockState(pos.add(-1, 0, 0)),
                MC.world.getBlockState(pos.add(0, 0, -1))
        };

        boolean isBedrock =
                (!blockStates[0].getMaterial().blocksMovement())
                        &&
                        (!blockStates[1].getMaterial().blocksMovement())
                        &&
                        (!blockStates[2].getMaterial().blocksMovement())
                        &&
                        (blockStates[3].getBlock().equals(Blocks.BEDROCK))
                        &&
                        (blockStates[4].getBlock().equals(Blocks.BEDROCK))
                        &&
                        (blockStates[5].getBlock().equals(Blocks.BEDROCK))
                        &&
                        (blockStates[6].getBlock().equals(Blocks.BEDROCK))
                        &&
                        (blockStates[7].getBlock().equals(Blocks.BEDROCK));

        if(isBedrock) return HoleType.BEDROCK;

        boolean bedrockOrObby =
                (!blockStates[0].getMaterial().blocksMovement())
                        &&
                        (!blockStates[1].getMaterial().blocksMovement())
                        &&
                        (!blockStates[2].getMaterial().blocksMovement())
                        &&
                        (blockStates[3].getBlock().equals(Blocks.BEDROCK) || blockStates[3].getBlock().equals(Blocks.OBSIDIAN))
                        &&
                        (blockStates[4].getBlock().equals(Blocks.BEDROCK) || blockStates[4].getBlock().equals(Blocks.OBSIDIAN))
                        &&
                        (blockStates[5].getBlock().equals(Blocks.BEDROCK) || blockStates[5].getBlock().equals(Blocks.OBSIDIAN))
                        &&
                        (blockStates[6].getBlock().equals(Blocks.BEDROCK) || blockStates[6].getBlock().equals(Blocks.OBSIDIAN))
                        &&
                        (blockStates[7].getBlock().equals(Blocks.BEDROCK) || blockStates[7].getBlock().equals(Blocks.OBSIDIAN));

        if(bedrockOrObby) return HoleType.OBBY;

        boolean isSolid =
                (!blockStates[0].getMaterial().blocksMovement())
                        &&
                        (!blockStates[1].getMaterial().blocksMovement())
                        &&
                        (!blockStates[2].getMaterial().blocksMovement())
                        &&
                        (blockStates[3].getMaterial().isSolid())
                        &&
                        (blockStates[4].getMaterial().isSolid())
                        &&
                        (blockStates[5].getMaterial().isSolid())
                        &&
                        (blockStates[6].getMaterial().isSolid())
                        &&
                        (blockStates[7].getMaterial().isSolid());

        if(isSolid) return HoleType.OTHER;

        return HoleType.NONE;
    }

    public static List<BlockPos> getAllInBox(final int x1, final int y1, final int z1, final int x2, final int y2, final int z2) {
        List<BlockPos> list = new ArrayList<>();
        // wanted to see how inline I could make this XD, good luck any future readers
        for(int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) for(int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) for(int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) list.add(new BlockPos(x, y, z));
        return list;
    }

    public static List<BlockPos> getBlocksInReachDistance() {
        List<BlockPos> cube = new ArrayList<>();
        for(int x = -4; x <= 4; x++)
            for(int y = -4; y <= 4; y++)
                for(int z = -4; z <= 4; z++)
                    cube.add(MC.player.getBlockPos().add(x, y, z));

        return cube.stream().filter(pos -> MC.player.squaredDistanceTo(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ())) <= 18.0625).collect(Collectors.toList());
    }

    //Credit to KAMI for code below
    public static double[] calculateLookAt(double px, double py, double pz, PlayerEntity me) {
        double dirx = me.getX() - px;
        double diry = me.getY() + me.getEyeHeight(me.getPose()) - py;
        double dirz = me.getZ() - pz;

        double len = Math.sqrt(dirx * dirx + diry * diry + dirz * dirz);

        dirx /= len;
        diry /= len;
        dirz /= len;

        double pitch = Math.asin(diry);
        double yaw = Math.atan2(dirz, dirx);

        //to degree
        pitch = pitch * 180.0d / Math.PI;
        yaw = yaw * 180.0d / Math.PI;

        yaw += 90f;

        return new double[]{yaw, pitch};
    }
    //End credit to Kami

    public static void rotate(float yaw, float pitch) {
        MC.player.setYaw(yaw);
        MC.player.setPitch(pitch);
    }

    public static void rotate(double[] rotations) {
        MC.player.setYaw((float) rotations[0]);
        MC.player.setPitch((float) rotations[1]);
    }

    public static void lookAtBlock(BlockPos blockToLookAt) {
        rotate(calculateLookAt(blockToLookAt.getX(), blockToLookAt.getY(), blockToLookAt.getZ(), MC.player));
    }

    public static String vectorToString(Vec3d vector, boolean... includeY) {
        boolean reallyIncludeY = includeY.length <= 0 || includeY[0];
        StringBuilder builder = new StringBuilder();
        builder.append('(');
        builder.append((int) Math.floor(vector.x));
        builder.append(", ");
        if(reallyIncludeY) {
            builder.append((int) Math.floor(vector.y));
            builder.append(", ");
        }
        builder.append((int) Math.floor(vector.z));
        builder.append(")");
        return builder.toString();
    }

    public static List<Entity> getTargets(boolean players, boolean friends, boolean teammates, boolean passive, boolean hostile, boolean nametagged, boolean bots) {
        return StreamSupport.stream(MC.world.getEntities().spliterator(), false).filter(entity -> isTarget(entity, players, friends, teammates, passive, hostile, nametagged, bots)).collect(Collectors.toList());
    }

    public static boolean isTarget(Entity entity, boolean players, boolean friends, boolean teammates, boolean passive, boolean hostile, boolean nametagged, boolean bots) {
        if(!(entity instanceof LivingEntity) || entity == MC.player) return false;

        if(players && entity instanceof PlayerEntity) {
            if(FriendManager.isFriend(((PlayerEntity) entity).getGameProfile().getName())) return friends;
            if(entity.getScoreboardTeam() == MC.player.getScoreboardTeam() && MC.player.getScoreboardTeam() != null) return teammates;
            return true;
        }
        if(!nametagged && entity.hasCustomName()) return false;
        if(passive && isPassive(entity)) return true;
        if(hostile && isHostile(entity)) return true;
        return bots && isBot(entity);
    }

    public static List<Entity> getPlayerTargets() {
        return getPlayerTargets(-1, false);
    }
    public static List<Entity> getPlayerTargets(double withinDistance) {
        return getPlayerTargets(withinDistance, true);
    }
    public static List<Entity> getPlayerTargets(double withinDistance, boolean doDistance) {
        List<Entity> targets = new ArrayList<>();

        targets.addAll(Streams.stream(MC.world.getEntities()).filter(entity -> isValidTarget(entity, withinDistance, doDistance)).collect(Collectors.toList()));
        targets.sort(Comparators.entityDistance);

        return targets;
    }

    public static boolean isValidTarget(Entity entity) {
        return isValidTarget(entity, -1, false);
    }
    public static boolean isValidTarget(Entity entity, double distance) {
        return isValidTarget(entity, distance, true);
    }
    public static boolean isValidTarget(Entity entity, double distance, boolean doDistance) {
        return (entity instanceof PlayerEntity || entity instanceof OtherClientPlayerEntity)
                && !friendCheck(entity)
                && !entity.isRemoved()
                && !hasZeroHealth(entity)
                && !shouldDistance(entity, distance, doDistance)
                && entity != MC.player;
    }

    private static boolean shouldDistance(Entity entity, double distance, boolean doDistance) {
        if(doDistance) return MC.player.distanceTo(entity) > distance;
        else return false;
    }

    public static boolean hasZeroHealth(PlayerEntity playerEntity) {
        return hasZeroHealth((Entity) playerEntity);
    }
    public static boolean hasZeroHealth(Entity entity) {
        if(entity instanceof PlayerEntity) {
            return (((PlayerEntity) entity).getHealth() <= 0);
        } else return false;
    }

    public static boolean friendCheck(PlayerEntity playerEntity) {
        return friendCheck((Entity) playerEntity);
    }
    public static boolean friendCheck(Entity entity) {
        if(entity instanceof PlayerEntity) {
            return FriendManager.isFriend(((PlayerEntity) entity).getGameProfile().getName());
        } else return false;
    }

    public static boolean isPassive(Entity entity) {
        if(entity instanceof IronGolemEntity && ((IronGolemEntity) entity).getAngryAt() == null) return true;
        else if(entity instanceof WolfEntity && (!((WolfEntity) entity).isAttacking() || ((WolfEntity) entity).getOwner() == MC.player)) return true;
        else if(entity instanceof EndermanEntity) return !((EndermanEntity) entity).isAngry();
        else return entity instanceof AmbientEntity || entity instanceof PassiveEntity || entity instanceof SquidEntity;
    }

    public static boolean isHostile(Entity entity) {
        if(entity instanceof IronGolemEntity) return ((IronGolemEntity) entity).getAngryAt() == MC.player.getUuid() && ((IronGolemEntity) entity).getAngryAt() != null;
        else if(entity instanceof WolfEntity) return ((WolfEntity) entity).isAttacking() && ((WolfEntity) entity).getOwner() != MC.player;
        else if(entity instanceof PiglinEntity) return ((PiglinEntity) entity).isAngryAt(MC.player);
        else if(entity instanceof EndermanEntity) return ((EndermanEntity) entity).isAngry();
        return entity.getType().getSpawnGroup() == SpawnGroup.MONSTER;
    }

    public static boolean isBot(Entity entity) {
        return entity instanceof PlayerEntity && entity.isInvisibleTo(MC.player) && !entity.isOnGround() && !entity.collides();
    }

    // Full sequence of packets sent from MC.player.jump()
    public static void fakeJumpSequence(int firstPacket, int lastPacket) {
        if(firstPacket <= 0 && lastPacket >= 0) MC.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(MC.player.getX(), MC.player.getY(), MC.player.getZ(), true));
        if(firstPacket <= 1 && lastPacket >= 1) MC.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(MC.player.getX(), MC.player.getY() + 0.41999998688698, MC.player.getZ(), true));
        if(firstPacket <= 2 && lastPacket >= 2) MC.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(MC.player.getX(), MC.player.getY() + 0.7531999805212, MC.player.getZ(), true));
        if(firstPacket <= 3 && lastPacket >= 3) MC.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(MC.player.getX(), MC.player.getY() + 1.00133597911214, MC.player.getZ(), true));
        if(firstPacket <= 4 && lastPacket >= 4) MC.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(MC.player.getX(), MC.player.getY() + 1.16610926093821, MC.player.getZ(), true));
        if(firstPacket <= 5 && lastPacket >= 5) MC.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(MC.player.getX(), MC.player.getY() + 1.24918707874468, MC.player.getZ(), true));
        if(firstPacket <= 6 && lastPacket >= 6) MC.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(MC.player.getX(), MC.player.getY() + 1.17675927506424, MC.player.getZ(), true));
        if(firstPacket <= 7 && lastPacket >= 7) MC.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(MC.player.getX(), MC.player.getY() + 1.02442408821369, MC.player.getZ(), true));
        if(firstPacket <= 8 && lastPacket >= 8) MC.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(MC.player.getX(), MC.player.getY() + 0.79673560066871, MC.player.getZ(), true));
        if(firstPacket <= 9 && lastPacket >= 9) MC.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(MC.player.getX(), MC.player.getY() + 0.49520087700593, MC.player.getZ(), true));
        if(firstPacket <= 10 && lastPacket >= 10) MC.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(MC.player.getX(), MC.player.getY() + 0.1212968405392, MC.player.getZ(), true));
        if(firstPacket <= 11 && lastPacket >= 11) MC.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(MC.player.getX(), MC.player.getY(), MC.player.getZ(), true));
    }

    public static BlockPos roundBlockPos(Vec3d vec) {
        return new BlockPos(vec.x, (int) Math.round(vec.y), vec.z);
    }

    public static void snapPlayer() {
        BlockPos lastPos = MC.player.isOnGround() ? WorldUtils.roundBlockPos(MC.player.getPos()) : MC.player.getBlockPos();
        snapPlayer(lastPos);
    }
    public static void snapPlayer(BlockPos lastPos) {
        double xPos = MC.player.getPos().x;
        double zPos = MC.player.getPos().z;

        if(Math.abs((lastPos.getX() + 0.5) - MC.player.getPos().x) >= 0.2) {
            int xDir = (lastPos.getX() + 0.5) - MC.player.getPos().x > 0 ? 1 : -1;
            xPos += 0.3 * xDir;
        }

        if(Math.abs((lastPos.getZ() + 0.5) - MC.player.getPos().z) >= 0.2) {
            int zDir = (lastPos.getZ() + 0.5) - MC.player.getPos().z > 0 ? 1 : -1;
            zPos += 0.3 * zDir;
        }

        MC.player.setVelocity(0, 0, 0);
        MC.player.setPosition(xPos, MC.player.getY(), zPos);
        MC.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(MC.player.getX(), MC.player.getY(), MC.player.getZ(), MC.player.isOnGround()));
    }

    public static boolean canBreakBlock(BlockPos blockPos) {
        final BlockState blockState = MC.world.getBlockState(blockPos);
        return blockState.getHardness(MC.world, blockPos) != -1;
    }

    public static List<BlockEntity> getBlockEntities() {
        List<BlockEntity> blockEntities = new ArrayList<>();
        ChunkPos chunkPos = MC.player.getChunkPos();
        int viewDistance = MC.options.viewDistance;
        for(int x = -viewDistance; x <= viewDistance; x++) {
            for(int z = -viewDistance; z <= viewDistance; z++) {
                WorldChunk worldChunk = MC.world.getChunkManager().getWorldChunk(chunkPos.x + x, chunkPos.z + z);
                if(worldChunk != null) blockEntities.addAll(worldChunk.getBlockEntities().values());
            }
        }


        return blockEntities;
    }

    public static enum InteractType {INTERACT, ATTACK, INTERACT_AT}
    public static Pair<InteractType, Integer> getInteractData(PlayerInteractEntityC2SPacket packet) {
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
        packet.write(buffer);
        int id = buffer.readVarInt();
        return new Pair<InteractType, Integer>(buffer.readEnumConstant(InteractType.class), id);
    }
}
