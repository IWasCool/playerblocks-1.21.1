package com.ourcreation.playerblocks;

import com.mojang.authlib.GameProfile;
import com.ourcreation.playerblocks.block.entity.PlayerChestEntity;
import com.ourcreation.playerblocks.block.entity.PlayerColoredEntity;
import com.ourcreation.playerblocks.color.PlayerColors;
import com.ourcreation.playerblocks.color.TeamColors;
import com.ourcreation.playerblocks.registry.ModContent;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.neoforge.NeoForgeAdapter;
import com.sk89q.worldedit.neoforge.internal.NBTConverter;
import com.sk89q.worldedit.util.concurrency.LazyReference;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;

public final class WorldEditColorTests {

    public static void worldEditPastesRebindBlocksAndKeepStorage(GameTestHelper helper) throws Exception {
        if (!ModList.get().isLoaded("worldedit")) {
            helper.succeed();
            return;
        }
        var level = helper.getLevel();
        var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "PBWorldEdit"),
                ClientInformation.createDefault());
        var colors = PlayerColors.get(level.getServer());
        var leader = UUID.randomUUID();
        TeamColors.registerProvider("audit:worldedit",
                (server, member) -> member.equals(player.getUUID()) ? new TeamColors.Team("audit:worldedit",
                        leader) : null);
        colors.set(leader, 0x16AA99);
        colors.set(player.getUUID(), 0xBB3311);
        var source = helper.absolutePos(new BlockPos(1, 1, 1));
        var target = helper.absolutePos(new BlockPos(3, 1, 1));
        level.setBlock(source.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(target.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(source, ModContent.CHEST.get().defaultBlockState(), 3);
        var chest = (PlayerChestEntity) level.getBlockEntity(source);
        chest.link(UUID.randomUUID());
        chest.setItem(0, new ItemStack(Items.DIAMOND, 7));
        var sourceData = chest.saveWithFullMetadata(level.registryAccess());
        try (var edit = WorldEdit.getInstance().newEditSessionBuilder().world(NeoForgeAdapter.adapt(level))
                .actor(NeoForgeAdapter.adaptPlayer(player)).maxBlocks(-1).build()) {
            var block = NeoForgeAdapter.adapt(chest.getBlockState())
                    .toBaseBlock(LazyReference.computed(NBTConverter.fromNative(sourceData)));
            edit.setBlock(NeoForgeAdapter.adapt(target), block);
            edit.setBlock(NeoForgeAdapter.adapt(target.south()),
                    NeoForgeAdapter.adapt(ModContent.PILLAR.get().defaultBlockState()));
            edit.flushSession();
        }
        helper.assertTrue(level.getBlockEntity(target) instanceof PlayerChestEntity pasted &&
                        player.getUUID().equals(pasted.owner()) && pasted.color() == 0x16AA99 &&
                        pasted.getItem(0).getCount() == 7,
                "Real WorldEdit chest paste replaces the author, uses team leader color, and preserves contents");
        helper.assertTrue(level.getBlockEntity(target.south()) instanceof PlayerColoredEntity pillar &&
                        player.getUUID().equals(pillar.owner()) && pillar.color() == 0x16AA99,
                "WorldEdit places colored patterned blocks with the actor's ownership");
        helper.assertTrue(!sourceData.getUUID("owner").equals(player.getUUID()),
                "WorldEdit template NBT remains unchanged");
        helper.succeed();
    }
}
