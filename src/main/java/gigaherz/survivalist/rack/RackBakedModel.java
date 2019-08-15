package gigaherz.survivalist.rack;

import com.google.common.collect.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import gigaherz.survivalist.Survivalist;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemModelMesher;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.model.ForgeBlockStateV1;
import net.minecraftforge.client.model.ICustomModelLoader;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.common.model.TRSRTransformation;
import net.minecraftforge.common.property.IExtendedBlockState;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RackBakedModel implements IBakedModel
{
    private final TextureAtlasSprite particle;
    private final IBakedModel rackBakedModel;

    private final TRSRTransformation[] itemTransforms;

    private final Map[] caches = new Map[] {
            Maps.newHashMap(), Maps.newHashMap(), Maps.newHashMap(), Maps.newHashMap()
    };

    public RackBakedModel(TextureAtlasSprite particle, IBakedModel rackBakedModel, TRSRTransformation[] itemTransforms)
    {
        this.particle = particle;
        this.rackBakedModel = rackBakedModel;
        this.itemTransforms = itemTransforms;
    }

    private static final EnumFacing[] faces = Streams.concat(Arrays.stream(EnumFacing.VALUES), Stream.of((EnumFacing)null)).toArray(EnumFacing[]::new);

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand)
    {
        List<BakedQuad> quads = Lists.newArrayList();

        BlockRenderLayer renderLayer = MinecraftForgeClient.getRenderLayer();
        if (renderLayer == BlockRenderLayer.SOLID)
        {
            quads.addAll(rackBakedModel.getQuads(state, side, rand));
        }
        else if (renderLayer == BlockRenderLayer.CUTOUT && side == null && state instanceof IExtendedBlockState)
        {
            RenderItem renderItem = Minecraft.getMinecraft().getRenderItem();
            World world = Minecraft.getMinecraft().world;

            IExtendedBlockState estate = ((IExtendedBlockState)state);
            RackItemsStateData items = estate.getValue(BlockRack.CONTAINED_ITEMS);

            for(int i = 0; i < 4; i++)
            {
                ItemStack stack = items.stacks[i];
                if (stack.isEmpty())
                    continue;

                IBakedModel model = renderItem.getItemModelWithOverrides(stack, world, null);
                Pair<? extends IBakedModel, Matrix4f> pair = model.handlePerspective(ItemCameraTransforms.TransformType.FIXED);
                model = pair.getLeft();
                Matrix4f matrix1 = pair.getRight();

                @SuppressWarnings("unchecked")
                Map<IBakedModel, List<BakedQuad>> cache = (Map<IBakedModel, List<BakedQuad>>)caches[i];

                List<BakedQuad> cachedQuads = cache.get(model);
                if (true)//cachedQuads == null)
                {
                    Matrix4f matrix = new Matrix4f();
                    matrix.setIdentity();

                    Matrix4f matrix2 = itemTransforms[i].getMatrix();
                    if (matrix2 != null)
                    {
                        matrix.mul(matrix2);
                    }

                    if (matrix1 != null)
                    {
                        matrix.mul(matrix1);
                    }

                    cachedQuads = Lists.newArrayList();
                    for (EnumFacing face : faces)
                    {
                        List<BakedQuad> inQuads = model.getQuads(null, face, rand);
                        List<BakedQuad> outQuads = QuadTransformer.processMany(inQuads, matrix);

                        cachedQuads.addAll(outQuads);
                    }
                    cache.put(model, cachedQuads);
                }
                quads.addAll(cachedQuads);
            }
        }

        return quads;
    }

    @Override
    public boolean isAmbientOcclusion()
    {
        return true;
    }

    @Override
    public boolean isGui3d()
    {
        return true;
    }

    @Override
    public boolean isBuiltInRenderer()
    {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleTexture()
    {
        return particle;
    }

    @Deprecated
    @Override
    public ItemCameraTransforms getItemCameraTransforms()
    {
        return ItemCameraTransforms.DEFAULT;
    }

    @Override
    public ItemOverrideList getOverrides()
    {
        return new ItemOverrideList(Collections.emptyList());
    }

    public static class Model implements IModel
    {
        private final ResourceLocation particle;
        private final ResourceLocation baseModel;
        private final TRSRTransformation[] transformations;

        public Model()
        {
            this.particle = null;
            this.baseModel = null;
            this.transformations = new TRSRTransformation[] {
                    TRSRTransformation.identity(),
                    TRSRTransformation.identity(),
                    TRSRTransformation.identity(),
                    TRSRTransformation.identity()
            };
        }

        public Model(@Nullable ResourceLocation particle, @Nullable ResourceLocation baseModel, TRSRTransformation[] transformations)
        {
            this.particle = particle;
            this.baseModel = baseModel;
            this.transformations = transformations;
        }

        @Override
        public Collection<ResourceLocation> getDependencies()
        {
            if (baseModel != null)
                return Collections.singletonList(baseModel);
            return Collections.emptyList();
        }

        @Override
        public Collection<ResourceLocation> getTextures()
        {
            if (particle != null)
                return Collections.singletonList(particle);
            return Collections.emptyList();
        }

        @Override
        public IBakedModel bake(IModelState state, VertexFormat format, Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter)
        {
            TextureAtlasSprite particleSprite = Minecraft.getMinecraft().getTextureMapBlocks().getMissingSprite();
            if (particle != null)
                particleSprite = bakedTextureGetter.apply(particle);

            IModel rackModel = baseModel == null ? ModelLoaderRegistry.getMissingModel() : ModelLoaderRegistry.getModelOrMissing(baseModel);
            IBakedModel rackBakedModel = rackModel.bake(state, format, bakedTextureGetter);

            Optional<TRSRTransformation> baseTransform = state.apply(Optional.empty());
            if (baseTransform.isPresent())
            {
                TRSRTransformation value = baseTransform.get();
                transformations[0] = value.compose(transformations[0]);
                transformations[1] = value.compose(transformations[1]);
                transformations[2] = value.compose(transformations[2]);
                transformations[3] = value.compose(transformations[3]);
            }

            return new RackBakedModel(particleSprite, rackBakedModel, transformations);
        }

        @Override
        public IModelState getDefaultState()
        {
            return TRSRTransformation.identity();
        }

        @Override
        public IModel retexture(ImmutableMap<String, String> textures)
        {
            String particleTexture = textures.get("particle");
            while (particleTexture != null && particleTexture.startsWith("#"))
            {
                particleTexture = textures.get(particleTexture.substring(1));
            }

            ResourceLocation rl = particleTexture != null ? new ResourceLocation(particleTexture) : null;
            return new Model(rl, baseModel, transformations);
        }

        private static final Gson GSON = (new GsonBuilder())
                .registerTypeAdapter(TRSRTransformation.class, ForgeBlockStateV1.TRSRDeserializer.INSTANCE)
                .create();
        @Override
        public IModel process(ImmutableMap<String, String> customData)
        {
            ResourceLocation baseModel = this.baseModel;
            if (customData.containsKey("base_model"))
            {
                String data = GSON.fromJson(customData.get("base_model"), String.class);
                baseModel = new ResourceLocation(data);
                baseModel = new ResourceLocation(baseModel.getNamespace(), "block/" + baseModel.getPath());
            }
            TRSRTransformation[] transformations = Arrays.copyOf(this.transformations, 4);
            for(int i=0;i<4;i++)
            {
                String key = "transform_" + i;
                if (customData.containsKey(key))
                {
                    transformations[i] = GSON.fromJson(customData.get(key), TRSRTransformation.class);
                }
            }
            return new Model(this.particle, baseModel, transformations);
        }
    }

    public static class ModelLoader implements ICustomModelLoader
    {
        public static final ResourceLocation FAKE_LOCATION = Survivalist.location("models/block/custom/rack_with_items");

        @Override
        public boolean accepts(ResourceLocation modelLocation)
        {
            if (!modelLocation.getNamespace().equals(Survivalist.MODID))
                return false;
            return modelLocation.equals(FAKE_LOCATION);
        }

        @Override
        public IModel loadModel(ResourceLocation modelLocation) throws Exception
        {
            return new Model();
        }

        @Override
        public void onResourceManagerReload(IResourceManager resourceManager)
        {
            // Nothing to do
        }
    }
}