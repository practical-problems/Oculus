package net.coderbot.iris.compat.dh;

import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiShadowCullingFrustum;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;

import net.coderbot.iris.pipeline.ShadowRenderer;
import net.coderbot.iris.shadows.frustum.CullEverythingFrustum;
import net.coderbot.iris.shadows.frustum.advanced.AdvancedShadowCullingFrustum;
import net.coderbot.iris.shadows.frustum.fallback.BoxCullingFrustum;
import net.minecraft.client.renderer.culling.Frustum;

/**
 * Lets Distant Horizons cull LOD sections in the shadow pass against Oculus's current shadow frustum, instead of
 * DH's default never-cull frustum. Oculus 1.8.0 does the same by mixing IDhApiShadowCullingFrustum into its four
 * frustum classes and binding ShadowRenderer.FRUSTUM; here one wrapper looks at whichever frustum is current.
 *
 * DH calls update() and then intersects() with world block coordinates once per LOD section. This relies on the order
 * of Oculus's shadow pass: ShadowRenderer.renderShadows assigns and prepares FRUSTUM before it draws the terrain layers,
 * and DH's shadow-pass culling only runs from that terrain draw (it is how DH knows it is in the shadow pass). If a
 * DH version ever culled earlier, this would see the previous frame's frustum, or null after a reload (draw all).
 * No frustum is stored here; FRUSTUM is read on every call.
 */
public final class DhShadowCullingFrustum implements IDhApiShadowCullingFrustum {
	private int worldMinY;
	private int worldMaxY;

	@Override
	public void update(int worldMinBlockY, int worldMaxBlockY, DhApiMat4f worldViewProjection) {
		this.worldMinY = worldMinBlockY;
		this.worldMaxY = worldMaxBlockY;
	}

	@Override
	public boolean intersects(int lodBlockPosMinX, int lodBlockPosMinZ, int lodBlockWidth, int lodDetailLevel) {
		Frustum frustum = ShadowRenderer.FRUSTUM;
		double minX = lodBlockPosMinX;
		double minZ = lodBlockPosMinZ;
		double maxX = lodBlockPosMinX + (double) lodBlockWidth;
		double maxZ = lodBlockPosMinZ + (double) lodBlockWidth;

		if (frustum instanceof AdvancedShadowCullingFrustum) {
			return ((AdvancedShadowCullingFrustum) frustum)
				.isBoxVisibleIgnoringDistance(minX, worldMinY, minZ, maxX, worldMaxY, maxZ);
		}
		if (frustum instanceof BoxCullingFrustum) {
			return ((BoxCullingFrustum) frustum).isBoxVisible(minX, worldMinY, minZ, maxX, worldMaxY, maxZ);
		}
		if (frustum instanceof CullEverythingFrustum) {
			return false;
		}
		// NonCullingFrustum, or no shadow frustum yet: draw everything (DH's default).
		return true;
	}
}
