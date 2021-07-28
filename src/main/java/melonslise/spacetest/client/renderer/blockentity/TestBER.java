package melonslise.spacetest.client.renderer.blockentity;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import com.mojang.math.Vector4f;

import melonslise.spacetest.client.init.SpaceTestRenderTypes;
import melonslise.spacetest.client.init.SpaceTestShaders;
import melonslise.spacetest.client.renderer.shader.ExtendedTextureTarget;
import melonslise.spacetest.common.blockentity.TestBlockEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TestBER implements BlockEntityRenderer<TestBlockEntity>
{
	public static ExtendedTextureTarget auxSampler;

	public TestBER(BlockEntityRendererProvider.Context ctx)
	{
		
	}

	@Override
	public void render(TestBlockEntity be, float frameTime, PoseStack mtx, MultiBufferSource buf, int light, int overlay)
	{
		Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
		Vec3 camPos = cam.getPosition();
		mtx.pushPose();
		mtx.translate(0.5d, 0.5d, 0.5d);

		// Init shader uniforms
		BlockPos pos = be.getBlockPos();
		Matrix4f modelView = mtx.last().pose().copy();
		Matrix4f invModelView = modelView.copy();
		invModelView.invert();
		Vector3f center = new Vector3f(pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f);
		// center.add((float) -camPos.x, (float) - camPos.y, (float) -camPos.z);
		Vector3f discDirection = new Vector3f(0f, 1f, 0.25f);
		discDirection.normalize();

		// Copy main framebuffer into aux framebuffer
		RenderTarget mainSampler = Minecraft.getInstance().getMainRenderTarget();
		if(auxSampler == null)
			auxSampler = new ExtendedTextureTarget(mainSampler);
		else if(auxSampler.width != mainSampler.width || auxSampler.height != mainSampler.height)
			auxSampler.resize(mainSampler.width, mainSampler.height, false);
		auxSampler.setClearColor(0f, 0f, 0f, 0f);
		auxSampler.copyColor(mainSampler);
		mainSampler.bindWrite(false);

		// Pass sampler and uniforms to shader
		SpaceTestShaders.blackHoleShader.setSampler("DiffuseSampler", auxSampler);
		SpaceTestShaders.blackHoleShader.setUniform("ModelViewInverseMat", invModelView);
		SpaceTestShaders.blackHoleShader.setUniform("Center", center);
		SpaceTestShaders.blackHoleShader.setUniform("Radius", 60f);
		SpaceTestShaders.blackHoleShader.setUniform("DiscDirection", discDirection);
		SpaceTestShaders.blackHoleShader.setUniform("DiscColor", new Vector4f(3.9533494f, 0.9935119f, 0f, 0.7f));
		SpaceTestShaders.blackHoleShader.setUniform("SSRadius", 0.2f);
		SpaceTestShaders.blackHoleShader.setUniform("DiscInnerRadius", 0.3f);
		SpaceTestShaders.blackHoleShader.setUniform("DiscOuterRadius", 64f);
		SpaceTestShaders.blackHoleShader.setUniform("G", 6f);

		sphere(buf.getBuffer(SpaceTestRenderTypes.BLACK_HOLE), mtx, 64f, 20, 20);
		mtx.popPose();
		((BufferSource) buf).endBatch();
	}

	@Override
	public int getViewDistance()
	{
		return 256;
	}

	public static Vector3f parametricSphere(float u, float v, float r)
	{
		return new Vector3f(Mth.cos(u) * Mth.sin(v) * r, Mth.cos(v) * r, Mth.sin(u) * Mth.sin(v) * r);
	}

	// FIXME thing in the center
	public static void sphere(VertexConsumer buf, PoseStack mtx, float radius, int longs, int lats)
	{
		Matrix4f last = mtx.last().pose();
		float startU = 0;
		float startV = 0;
		float endU = Mth.PI * 2;
		float endV = Mth.PI;
		float stepU = (endU - startU) / longs;
		float stepV=(endV - startV) / lats;
		for(int i = 0; i < longs; ++i)
		{
			// U-points
			for(int j = 0; j < lats; ++j)
			{
				// V-points
				float u = i * stepU + startU;
				float v = j * stepV + startV;
				float un = (i +1 == longs) ? endU : (i + 1) * stepU + startU;
				float vn = (j+1 == lats) ? endU : (j + 1) * stepV + startV;
				Vector3f p0 = parametricSphere(u, v, radius);
				Vector3f p1 = parametricSphere(u, vn, radius);
				Vector3f p2 = parametricSphere(un, v, radius);
				Vector3f p3 = parametricSphere(un, vn, radius);
				buf.vertex(last, p0.x(), p0.y(), p0.z()).endVertex();
				buf.vertex(last, p2.x(), p2.y(), p2.z()).endVertex();
				buf.vertex(last, p1.x(), p1.y(), p1.z()).endVertex();

				buf.vertex(last, p3.x(), p3.y(), p3.z()).endVertex();
				buf.vertex(last, p1.x(), p1.y(), p1.z()).endVertex();
				buf.vertex(last, p2.x(), p2.y(), p2.z()).endVertex();
			}
		}
	}
}