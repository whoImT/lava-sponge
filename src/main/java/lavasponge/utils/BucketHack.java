package lavasponge.utils;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.apache.commons.io.output.AppendableOutputStream;
import org.lwjgl.system.CallbackI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

public class BucketHack {

    private ClientPlayerEntity player;
    private Vec3d playerVec;
    private Vec3d destroyerVec;
    private Vec3d pickupVec;
    private ArrayList<BlockPos> blockList;
    private World world;
    private MinecraftClient minecraft;
    private boolean placeBlockStateHackFix;
    private boolean lavaDestroyed;
    private HashMap<BlockPos, BlockState> map = new HashMap<>();

    public BucketHack(MinecraftClient minecraft) {
        this.minecraft = minecraft;
        this.player = minecraft.player;
        this.world = minecraft.world;
        System.out.println("new buckethack");
    }

    public void useBucketer() {
        playerVec = player.getCameraPosVec(1.0f);
        blockList = genBlockList();
        System.out.println("blocklist gen done");

        ArrayList<BlockPos> lavaList = getList();
        System.out.println(lavaList);
        if(lavaList.size() == 0) return;
        for(BlockPos pos : lavaList){
            System.out.println(pos + " dst: " + new Vec3d(pos.getX(), pos.getY(),pos.getZ()).add(0.5, 0.5, 0.5).distanceTo(playerVec));
        }
        System.out.println("lava list gen + check");

        getDestroyerBlock();
        if (destroyerVec == null || pickupVec == null) {
            return;
        }
        System.out.println("destroyerVec: " + destroyerVec);
        System.out.println("pcikupVec: " + pickupVec);
        System.out.println("destroyblock gen + check");

        //not sure about this
        Item item = player.getMainHandStack().getItem();
        System.out.println(item);
        if(item == Items.LAVA_BUCKET || item == Items.BUCKET) {
            System.out.println("bucket detected");
            placeBlockStateHackFix = true;
            if(item == Items.LAVA_BUCKET) {
                placeOnDestroyerBlock(destroyerVec);
            }
            destroyLava(lavaList);
            placeOnDestroyerBlock(pickupVec);
            if(lavaDestroyed) player.playSound(SoundEvents.ITEM_BUCKET_FILL, 1.0F, 1.0F);
        }
    }

    private ArrayList<BlockPos> getList() {
        ArrayList<BlockPos> lavaList = new ArrayList<>();
        for(BlockPos pos : blockList) {
            BlockState blockstate = world.getBlockState(pos);
            setBlockState(pos, blockstate);
            Block block = blockstate.getBlock();
            if(blockstate.getFluidState().getFluid() == Fluids.LAVA) { //not sure
                lavaList.add(pos);
            }
            //else if(block == Blocks.FLOWING_LAVA && Block.getRawIdFromState(blockstate) == 0) {//not sure
            //    lavaList.add(pos);
            //}
        }
        return lavaList;
    }

    public ArrayList<BlockPos> genBlockList() {
        BlockPos playerPos = player.getBlockPos();
        ArrayList<BlockPos> list = new ArrayList<BlockPos>();

        for(int x = -5; x < 6; x++) {
            for(int y = -5; y < 6; y++) {
                for(int z = -5; z < 6; z++) {
                    list.add(new BlockPos(playerPos.getX() + x, playerPos.getY() + y, playerPos.getZ() + z));
                }
            }
        }

        list.sort(Comparator.comparing());

        list.sort((BlockPos b1, BlockPos b2) -> {
            Vec3d b1vec = new Vec3d(b1.getX(), b1.getY(), b2.getZ()).add(0.5, 0.5, 0.5);
            Vec3d b2vec = new Vec3d(b2.getX(), b2.getY(), b2.getZ()).add(0.5, 0.5, 0.5);
            double b1dist = b1vec.distanceTo(playerVec);
            double b2dist = b2vec.distanceTo(playerVec);
            //double b1dist = b1.getSquaredDistance(playerVec.getX(),playerVec.getY(), playerVec.getZ(),false );
            //double b2dist = b2.getSquaredDistance(playerVec.getX(),playerVec.getY(), playerVec.getZ(),false );

            if (b1dist == b2dist) {
                return 0;
            } else if (b1dist > b2dist) {
                return 1;
            } else {
                return -1;
            }
        });

        return list;
    }

    private void getDestroyerBlock() {
        for(int i = blockList.size() - 1; i >= 0; i--) {
            BlockPos pos = blockList.get(i);
            BlockState blockstate = world.getBlockState(pos);
            Block block = blockstate.getBlock();
            //if(block.canCollideCheck(blockstate, false)) {
                VoxelShape collisionShape = blockstate.getCollisionShape(this.world, pos);
                if(collisionShape != null) {
                    Vec3d dest = getBlockSpot(pos, playerVec, new Box(pos), RaycastContext.FluidHandling.NONE);

                    if (dest != null) {
                        float yaw = getYaw(dest);
                        float pitch = getPitch(dest);
                        BlockHitResult ray = rayTrace(world, pitch, yaw, RaycastContext.FluidHandling.NONE);
                        if (ray == null) continue;
                        pos = ray.getBlockPos().offset(ray.getSide());
                        BlockState blockState = getBlockState(pos);
                        setBlockState(pos, Blocks.STONE.getDefaultState());
                        pickupVec = getBlockSpot(pos, playerVec, new Box(pos), RaycastContext.FluidHandling.NONE);
                        setBlockState(pos, blockState);
                        destroyerVec = dest;
                        return;
                    }
                }
            //}
        }
    }

    private void destroyLava(ArrayList<BlockPos> lavaList){
        System.out.println(lavaList.size());
        int i = 0;
        for(BlockPos pos : lavaList) {
            lavaDestroyed = true;
            Vec3d v = getBlockSpot(pos, playerVec, new Box(pos), RaycastContext.FluidHandling.SOURCE_ONLY);
            System.out.println("lavaVec: " + v);
            if(v == null) continue;
            pickupLava(pos, v);
            placeOnDestroyerBlock(destroyerVec);
            i++;
            System.out.println("block destroyed");
        }
        System.out.println("total lava destroyed: " + i);
    }

    private void pickupLava(BlockPos pos, Vec3d v) {
        if(v == null) return;

        player.networkHandler.sendPacket(new PlayerMoveC2SPacket.Both(player.getX(), player.getY(), player.getZ(), (float)getYaw(v), (float)getPitch(v), player.isOnGround()));
        player.networkHandler.sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND));
        setBlockState(pos, Blocks.AIR.getDefaultState());
    }

    private void placeOnDestroyerBlock(Vec3d v) {
        if(destroyerVec == null) return;

        player.networkHandler.sendPacket(new PlayerMoveC2SPacket.Both(player.getX(), player.getY(), player.getZ(), (float)getYaw(v), (float)getPitch(v), player.isOnGround()));
        player.networkHandler.sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND));

        setLavaOnes();
    }

    private void setLavaOnes() {
        if(placeBlockStateHackFix){
            BlockHitResult ray = rayTrace(world, getPitch(destroyerVec), getYaw(destroyerVec), RaycastContext.FluidHandling.NONE);
            if(ray != null){
                BlockPos pos = ray.getBlockPos().offset(ray.getSide());
                //totally not sure about this
                setBlockState(pos, Fluids.FLOWING_LAVA.getDefaultState().getBlockState());
            }
        }
        placeBlockStateHackFix = false;
    }

    private float getYaw(Vec3d v) {
        double x = v.x - playerVec.x;
        double z = v.z - playerVec.z;
        if(x == z) return 0;
        return (float) (Math.atan2(x, z) * 180 / Math.PI * -1);
    }

    private float getPitch(Vec3d v) {
        double x = v.x - playerVec.x;
        double y = v.y - playerVec.y;
        double z = v.z - playerVec.z;

        return (float) (Math.atan(y / Math.sqrt(x*x+z*z)) * 180 / Math.PI * -1);
    }

    public Vec3d getBlockSpot(BlockPos pos, Vec3d vec, Box bb, RaycastContext.FluidHandling fluidHandling)
    {
        double d0 = 1.0D / ((bb.getMax(Direction.Axis.X) - bb.getMin(Direction.Axis.X)) * 2.0D + 1.0D);
        double d1 = 1.0D / ((bb.getMax(Direction.Axis.Y) - bb.getMin(Direction.Axis.Y)) * 2.0D + 1.0D);
        double d2 = 1.0D / ((bb.getMax(Direction.Axis.Z) - bb.getMin(Direction.Axis.Z)) * 2.0D + 1.0D);
        double d3 = (1.0D - Math.floor(1.0D / d0) * d0) / 2.0D;
        double d4 = (1.0D - Math.floor(1.0D / d2) * d2) / 2.0D;

        if (d0 >= 0.0D && d1 >= 0.0D && d2 >= 0.0D)
        {
            int i = 0;
            int j = 0;

            for (float f = 0.0F; f <= 1.0F; f = (float)((double)f + d0))
            {
                for (float f1 = 0.0F; f1 <= 1.0F; f1 = (float)((double)f1 + d1))
                {
                    for (float f2 = 0.0F; f2 <= 1.0F; f2 = (float)((double)f2 + d2))
                    {
                        double d5 = bb.getMin(Direction.Axis.X) + (bb.getMax(Direction.Axis.X) - bb.getMin(Direction.Axis.X)) * (double)f;
                        double d6 = bb.getMin(Direction.Axis.Y) + (bb.getMax(Direction.Axis.Y) - bb.getMin(Direction.Axis.Y)) * (double)f1;
                        double d7 = bb.getMin(Direction.Axis.Z) + (bb.getMax(Direction.Axis.Z) - bb.getMin(Direction.Axis.Z)) * (double)f2;
                        Vec3d spot = new Vec3d(d5 + d3, d6, d7 + d4);

                        float yaw = getYaw(spot);
                        float pitch = getPitch(spot);
                        BlockHitResult ray = rayTrace(world, pitch, yaw, fluidHandling);
                        if(ray != null && ray.getBlockPos().equals(pos)) {
                            return spot;
                        }
                    }
                }
            }

            return null;
        }
        else
        {
            return null;
        }
    }

    protected BlockHitResult rayTrace(World worldIn, float rotationPitch, float rotationYaw, RaycastContext.FluidHandling fluidHandling)
    {
        float f = rotationPitch;
        float f1 = rotationYaw;
        double d0 = playerVec.getX();
        double d1 = playerVec.getY();
        double d2 = playerVec.getZ();
        Vec3d vec3d = new Vec3d(d0, d1, d2);
        float f2 = MathHelper.cos(-f1 * 0.017453292F - (float)Math.PI);
        float f3 = MathHelper.sin(-f1 * 0.017453292F - (float)Math.PI);
        float f4 = -MathHelper.cos(-f * 0.017453292F);
        float f5 = MathHelper.sin(-f * 0.017453292F);
        float f6 = f3 * f4;
        float f7 = f2 * f4;
        double d3 = 5.0D;
        Vec3d vec3d1 = vec3d.add((double)f6 * d3, (double)f5 * d3, (double)f7 * d3);
        return worldIn.raycast(new RaycastContext(vec3d, vec3d1, RaycastContext.ShapeType.OUTLINE, fluidHandling, player));
    }

    private void setBlockState(BlockPos pos, BlockState blockstate) {
        map.put(pos, blockstate);
    }

    private BlockState getBlockState(BlockPos pos) {
        return map.get(pos);
    }

    //public RayTraceResult rayTraceBlocks(Vec3d start, Vec3d end, boolean stopOnLiquid, boolean ignoreBlockWithoutBoundingBox, boolean returnLastUncollidableBlock)
    //{
    //    if (!Double.isNaN(start.x) && !Double.isNaN(start.y) && !Double.isNaN(start.z))
    //    {
    //        if (!Double.isNaN(end.x) && !Double.isNaN(end.y) && !Double.isNaN(end.z))
    //        {
    //            int endX = MathHelper.floor(end.x);
    //            int endY = MathHelper.floor(end.y);
    //            int endZ = MathHelper.floor(end.z);
    //            int startX = MathHelper.floor(start.x);
    //            int startY = MathHelper.floor(start.y);
    //            int startZ = MathHelper.floor(start.z);
    //            BlockPos blockpos = new BlockPos(startX, startY, startZ);
    //            BlockState blockstate = getBlockState(blockpos);
    //            Block block = blockstate.getBlock();
//
    //            if ((!ignoreBlockWithoutBoundingBox || blockstate.getCollisionBoundingBox(world, blockpos) != Block.NULL_AABB) && block.canCollideCheck(iblockstate, stopOnLiquid))
    //            {
    //                RayTraceResult raytraceresult = blockstate.collisionRayTrace(world, blockpos, start, end);
//
    //                if (raytraceresult != null)
    //                {
    //                    return raytraceresult;
    //                }
    //            }
//
    //            RayTraceResult raytraceresult2 = null;
    //            int k1 = 200;
//
    //            while (k1-- >= 0)
    //            {
    //                if (Double.isNaN(start.x) || Double.isNaN(start.y) || Double.isNaN(start.z))
    //                {
    //                    return null;
    //                }
//
    //                if (startX == endX && startY == endY && startZ == endZ)
    //                {
    //                    return returnLastUncollidableBlock ? raytraceresult2 : null;
    //                }
//
    //                boolean flag2 = true;
    //                boolean flag = true;
    //                boolean flag1 = true;
    //                double d0 = 999.0D;
    //                double d1 = 999.0D;
    //                double d2 = 999.0D;
//
    //                if (endX > startX)
    //                {
    //                    d0 = (double)startX + 1.0D;
    //                }
    //                else if (endX < startX)
    //                {
    //                    d0 = (double)startX + 0.0D;
    //                }
    //                else
    //                {
    //                    flag2 = false;
    //                }
//
    //                if (endY > startY)
    //                {
    //                    d1 = (double)startY + 1.0D;
    //                }
    //                else if (endY < startY)
    //                {
    //                    d1 = (double)startY + 0.0D;
    //                }
    //                else
    //                {
    //                    flag = false;
    //                }
//
    //                if (endZ > startZ)
    //                {
    //                    d2 = (double)startZ + 1.0D;
    //                }
    //                else if (endZ < startZ)
    //                {
    //                    d2 = (double)startZ + 0.0D;
    //                }
    //                else
    //                {
    //                    flag1 = false;
    //                }
//
    //                double d3 = 999.0D;
    //                double d4 = 999.0D;
    //                double d5 = 999.0D;
    //                double dx = end.x - start.x;
    //                double dy = end.y - start.y;
    //                double dz = end.z - start.z;
//
    //                if (flag2)
    //                {
    //                    d3 = (d0 - start.x) / dx;
    //                }
//
    //                if (flag)
    //                {
    //                    d4 = (d1 - start.y) / dy;
    //                }
//
    //                if (flag1)
    //                {
    //                    d5 = (d2 - start.z) / dz;
    //                }
//
    //                if (d3 == -0.0D)
    //                {
    //                    d3 = -1.0E-4D;
    //                }
//
    //                if (d4 == -0.0D)
    //                {
    //                    d4 = -1.0E-4D;
    //                }
//
    //                if (d5 == -0.0D)
    //                {
    //                    d5 = -1.0E-4D;
    //                }
//
    //                EnumFacing enumfacing;
//
    //                if (d3 < d4 && d3 < d5)
    //                {
    //                    enumfacing = endX > startX ? EnumFacing.WEST : EnumFacing.EAST;
    //                    start = new Vec3d(d0, start.y + dy * d3, start.z + dz * d3);
    //                }
    //                else if (d4 < d5)
    //                {
    //                    enumfacing = endY > startY ? EnumFacing.DOWN : EnumFacing.UP;
    //                    start = new Vec3d(start.x + dx * d4, d1, start.z + dz * d4);
    //                }
    //                else
    //                {
    //                    enumfacing = endZ > startZ ? EnumFacing.NORTH : EnumFacing.SOUTH;
    //                    start = new Vec3d(start.x + dx * d5, start.y + dy * d5, d2);
    //                }
//
    //                startX = MathHelper.floor(start.x) - (enumfacing == EnumFacing.EAST ? 1 : 0);
    //                startY = MathHelper.floor(start.y) - (enumfacing == EnumFacing.UP ? 1 : 0);
    //                startZ = MathHelper.floor(start.z) - (enumfacing == EnumFacing.SOUTH ? 1 : 0);
    //                blockpos = new BlockPos(startX, startY, startZ);
    //                BlockState blockstate1 = getBlockState(blockpos);
    //                if(blockstate1 == null) return null;
    //                Block block1 = blockstate1.getBlock();
//
    //                if (!ignoreBlockWithoutBoundingBox || blockstate1.getMaterial() == Material.PORTAL || blockstate1.getCollisionBoundingBox(world, blockpos) != Block.NULL_AABB)
    //                {
    //                    if (block1.canCollideCheck(blockstate1, stopOnLiquid))
    //                    {
    //                        RayTraceResult raytraceresult1 = blockstate1.collisionRayTrace(world, blockpos, start, end);
//
    //                        if (raytraceresult1 != null)
    //                        {
    //                            return raytraceresult1;
    //                        }
    //                    }
    //                    else
    //                    {
    //                        raytraceresult2 = new RayTraceResult(RayTraceResult.Type.MISS, start, enumfacing, blockpos);
    //                    }
    //                }
    //            }
//
    //            return returnLastUncollidableBlock ? raytraceresult2 : null;
    //        }
    //        else
    //        {
    //            return null;
    //        }
    //    }
    //    else
    //    {
    //        return null;
    //    }
    //}


}