package minecrafttransportsimulator.rendering.components;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import minecrafttransportsimulator.entities.components.AEntityC_Definable;
import minecrafttransportsimulator.entities.instances.PartGroundDevice;
import minecrafttransportsimulator.jsondefs.AJSONPartProvider;
import minecrafttransportsimulator.jsondefs.JSONAnimatedObject;
import minecrafttransportsimulator.jsondefs.JSONAnimationDefinition;
import minecrafttransportsimulator.mcinterface.InterfaceRender;

/**This class represents an object that can be rendered from an model.  This object is a set of
 * faces that are rendered during the main rendering routine.  Various transforms may be performed on
 * this object via the extended rendering classes.  These transforms are applied to the mesh prior
 * to rendering, either manipulating the mesh directly, or manipulating the OpenGL state.  In all
 * cases, there will be at least one transform on any model object.  This being if it's solid or translucent.
 * Other transforms are added as required.
 *
 * @author don_bruce
 */
public class RenderableModelObject<AnimationEntity extends AEntityC_Definable<?>> extends RenderableTransform<AnimationEntity>{
	private final String objectName;
	public final String applyAfter;
	public final int cachedVertexIndex;
	
	private static final Map<String, Map<String, Integer>> cachedVertexIndexLists = new HashMap<String, Map<String, Integer>>();
	
	public RenderableModelObject(String modelName, String objectName, JSONAnimatedObject definition, Float[][] vertices, AnimationEntity entity){
		super(definition != null ? definition.animations : new ArrayList<JSONAnimationDefinition>());
		
		//Cache the displayList, if we haven't already.
		if(!cachedVertexIndexLists.containsKey(modelName) || !cachedVertexIndexLists.get(modelName).containsKey(objectName)){
			if(!cachedVertexIndexLists.containsKey(modelName)){
				cachedVertexIndexLists.put(modelName, new HashMap<String, Integer>());
			}
			cachedVertexIndexLists.get(modelName).put(objectName, InterfaceRender.cacheVertices(vertices));
		}
		this.cachedVertexIndex = cachedVertexIndexLists.get(modelName).get(objectName);
		
		//Set all parameters for the appropriate transforms.
		this.objectName = objectName;
		if(definition != null){
			this.applyAfter = definition.applyAfter;
		}else{
			this.applyAfter = null;
			//Roller found.  Create a transform for it.
			if(objectName.toLowerCase().contains("roller")){
				transforms.add(new TransformTreadRoller<AnimationEntity>(objectName, vertices, ((AJSONPartProvider) entity.definition).parts));
			}else if(entity instanceof PartGroundDevice){
				PartGroundDevice grounder = (PartGroundDevice) entity;
				if(grounder.definition.ground != null && grounder.definition.ground.isTread){
					//Found tread-based ground device.  Need a transform for tread rendering.
					transforms.add(new TransformTreadRenderer<AnimationEntity>(cachedVertexIndexLists.get(modelName).get(objectName)));
				}
			}
		}
		
		//Get the light transform, if we have it.
		TransformLight<AnimationEntity> lightTransform = null;
		if(objectName.contains("&")){
			lightTransform = new TransformLight<AnimationEntity>(modelName, objectName, vertices);
		}
		
		//If we have a light transform, and it's not a light-up texture, don't add other transforms.
		//If it is a light-up texture, add other transforms before the light.  This prevents us from
		//calling the light transform's calls if we shouldn't even be rendering it.
		if(lightTransform == null || lightTransform.isLightupTexture){
			if(objectName.toLowerCase().contains("translucent")){
				transforms.add(new TransformTranslucent<AnimationEntity>());
			}else{
				transforms.add(new TransformSolid<AnimationEntity>());
			}
		}
		
		if(lightTransform != null){
			transforms.add(lightTransform);
		}
		if(objectName.toLowerCase().contains("window")){
			transforms.add(new TransformWindow<AnimationEntity>(vertices));
		}
		if(objectName.toLowerCase().startsWith("url") || objectName.toLowerCase().endsWith("url")){
			transforms.add(new TransformOnlineTexture<AnimationEntity>(objectName));
		}
	}
	
	/**
	 *  Renders this object, applying any transforms that need to happen.  This method also
	 *  renders any objects that depend on this object's transforms after rendering.
	 */
	public void render(AnimationEntity entity, boolean blendingEnabled, float partialTicks, List<RenderableModelObject<AnimationEntity>> allObjects){
		GL11.glPushMatrix();
		if(doPreRenderTransforms(entity, blendingEnabled, partialTicks)){
			if(renderModelWithBlendState(entity, blendingEnabled)){
				//Render the model.
				InterfaceRender.renderVertices(cachedVertexIndex);
			}
			
			//Do post-render logic.
			doPostRenderTransforms(entity, blendingEnabled, partialTicks);
			
			//Render text on this object.
			if(!blendingEnabled){
				if(InterfaceRender.renderTextMarkings(entity, objectName)){
					InterfaceRender.recallTexture();
				}
			}
			
			//Render any parts that depend on us before we pop our state.
			for(RenderableModelObject<AnimationEntity> modelObject : allObjects){
				if(objectName.equals(modelObject.applyAfter)){
					modelObject.render(entity, blendingEnabled, partialTicks, allObjects);
				}
			}
		}
		
		//Pop state.
		GL11.glPopMatrix();
	}
}
