package net.coderbot.iris.pipeline.transform.parameter;
import net.coderbot.iris.pipeline.transform.Patch;
import net.coderbot.iris.gl.blending.AlphaTest;
import net.coderbot.iris.shaderpack.texture.TextureStage;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
public final class DHParameters extends Parameters {
    public DHParameters() { this(Patch.DH); }
    /** Patch.DH for LOD terrain, Patch.DH_GENERIC for DH's generic objects (clouds, beacon beams). */
    public DHParameters(Patch patch) { super(patch, new Object2ObjectOpenHashMap<>()); }
    public TextureStage getTextureStage() { return TextureStage.GBUFFERS_AND_SHADOW; }
    @Override
    public AlphaTest getAlphaTest() { return AlphaTest.ALWAYS; }
}
