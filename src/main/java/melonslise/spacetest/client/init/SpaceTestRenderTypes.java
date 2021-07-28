package melonslise.spacetest.client.init;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;

import melonslise.spacetest.SpaceTest;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class SpaceTestRenderTypes extends RenderType
{
	public static final RenderType BLACK_HOLE = RenderType.create(SpaceTest.ID, DefaultVertexFormat.POSITION, VertexFormat.Mode.TRIANGLES, 256, false, false, RenderType.CompositeState.builder()
		.setShaderState(SpaceTestShaders.BLACK_HOLE_SHADER_STATE)
		.setTextureState(new RenderStateShard.TextureStateShard(new ResourceLocation(SpaceTest.ID, "textures/misc/noise.png"), false, false))
		.setCullState(NO_CULL) // FIXME do we need this?
		.createCompositeState(false));

	public SpaceTestRenderTypes(String p_173178_, VertexFormat p_173179_, Mode p_173180_, int p_173181_,
			boolean p_173182_, boolean p_173183_, Runnable p_173184_, Runnable p_173185_)
	{
		super(p_173178_, p_173179_, p_173180_, p_173181_, p_173182_, p_173183_, p_173184_, p_173185_);
		// TODO Auto-generated constructor stub
	}
}