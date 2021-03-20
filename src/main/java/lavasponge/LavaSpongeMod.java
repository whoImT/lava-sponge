package lavasponge;

import lavasponge.utils.BucketHack;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.client.ClientTickCallback;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.LiteralText;
import org.lwjgl.glfw.GLFW;


public class LavaSpongeMod implements ModInitializer {

	@Override
	public void onInitialize(){
		KeyBinding lavaspongeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.lava-sponge.lavasponge-key", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.category.first.lavasponge"));

		ClientTickCallback.EVENT.register(client -> {
			while (lavaspongeKey.wasPressed()){
				new BucketHack(client).useBucketer();
			}
		});

}




}
