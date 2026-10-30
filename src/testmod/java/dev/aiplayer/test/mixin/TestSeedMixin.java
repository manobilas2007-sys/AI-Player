package dev.aiplayer.test.mixin;

import net.minecraft.test.TestServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Test-only: vanilla TestServer otherwise always generates seed zero. */
@Mixin(value=TestServer.class,remap=false)
public abstract class TestSeedMixin {
    @ModifyArg(method="<clinit>",at=@At(value="INVOKE",
        target="Lnet/minecraft/world/gen/GeneratorOptions;<init>(JZZ)V"),index=0,remap=false)
    private static long aiPlayerScenarioSeed(long original){return Long.getLong("aiplayer.gametest.seed",original);}
}
