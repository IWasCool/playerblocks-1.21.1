package com.ourcreation.playerblocks;

import com.ourcreation.playerblocks.color.PlayerColors;
import com.ourcreation.playerblocks.color.TeamColors;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PlayerBlocks.MODID)
@PrefixGameTestTemplate(false)
public final class ReleaseAuditTests {

    @GameTest(template = "empty")
    public static void bulkColorCachingAndPersistence(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var colors = new PlayerColors();
        var owner = UUID.randomUUID();
        var calls = new AtomicInteger();
        TeamColors.registerProvider("audit:cache", (world, member) -> {
            if (member.equals(owner)) {
                calls.incrementAndGet();
                return new TeamColors.Team("audit:cache", owner);
            }
            return null;
        });
        for (int index = 0; index < 2000; index++) {
            colors.slot(owner);
            colors.ensureTeam(server, owner);
        }
        helper.assertTrue(calls.get() == 1 && colors.slotCount() == 1,
                "Two thousand bulk placements resolve their owner only once per tick");
        helper.assertTrue(colors.paletteChanged(), "First palette is sent");
        colors.set(owner, 0x38AACC);
        var snapshot = colors.palette();
        snapshot.putInt("1", 0);
        helper.assertTrue(colors.paletteChanged() && colors.palette().getInt("1") == 0x38AACC,
                "Reading or editing a snapshot never consumes or corrupts a pending palette update");
        helper.assertTrue(!colors.paletteChanged(), "Unchanged palettes do not resend");
        for (int index = 0; index < 1000; index++) {
            colors.slot(UUID.randomUUID());
        }
        var loaded = PlayerColors.load(colors.save(new CompoundTag(), helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess());
        helper.assertTrue(
                loaded.slotCount() == 1001 && loaded.slot(owner) == 1 && loaded.slot(UUID.randomUUID()) == 1002,
                "Slot index rebuilds without collisions after saving/loading");
        var duplicate = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        var damaged = new CompoundTag();
        var duplicateSlots = new CompoundTag();
        duplicateSlots.putInt(duplicate.toString(), 1);
        duplicateSlots.putInt(duplicate.toString().toUpperCase(Locale.ROOT), 2);
        damaged.put("slots", duplicateSlots);
        var repaired = PlayerColors.load(damaged, helper.getLevel().registryAccess());
        int first = repaired.slot(duplicate);
        helper.assertTrue(repaired.slotCount() == 1 && repaired.slot(UUID.randomUUID()) == (first == 1 ? 2 : 1),
                "Duplicate UUID spellings in damaged saved data do not reserve ghost slots");
        helper.succeed();
    }
}
