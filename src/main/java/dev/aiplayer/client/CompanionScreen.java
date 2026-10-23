package dev.aiplayer.client;
import dev.aiplayer.ai.Mode;
import dev.aiplayer.screen.CompanionMenu;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

public class CompanionScreen extends HandledScreen<CompanionMenu> {
    private ButtonWidget auto;
    public CompanionScreen(CompanionMenu menu,PlayerInventory inv,Text title){super(menu,inv,Text.literal("AI Player"));backgroundWidth=176;backgroundHeight=238;playerInventoryTitleY=0;}
    @Override protected void init(){
        super.init();
        button("Seguir",8,23,51,0);button("Proteger",62,23,57,1);button("Esperar",122,23,46,2);
        button("Vir aqui",8,42,64,3);auto=button("Autoequipar",76,42,92,4);
    }
    private ButtonWidget button(String label,int dx,int dy,int width,int command){
        return addDrawableChild(ButtonWidget.builder(Text.literal(label),b->{
            if(client!=null && client.interactionManager!=null)client.interactionManager.clickButton(handler.syncId,command);
        }).dimensions(x+dx,y+dy,width,18).build());
    }
    @Override protected void handledScreenTick(){super.handledScreenTick();auto.setMessage(Text.literal("Auto: "+(handler.value(2)==1?"sim":"não")));}
    @Override protected void drawBackground(DrawContext ctx,float delta,int mouseX,int mouseY){
        ctx.fill(x,y,x+backgroundWidth,y+backgroundHeight,0xffc6c6c6);
        ctx.fill(x+2,y+2,x+174,y+21,0xff243b4a);
        for(var slot:handler.slots){
            int sx=x+slot.x,sy=y+slot.y;
            ctx.fill(sx-1,sy-1,sx+17,sy+17,0xff373737);
            ctx.fill(sx,sy,sx+16,sy+16,0xff8b8b8b);
        }
    }
    @Override protected void drawForeground(DrawContext ctx,int mouseX,int mouseY){
        ctx.drawText(textRenderer,"AI Player | "+Mode.fromId(handler.value(0)).label,8,3,0xffffff,false);
        ctx.drawText(textRenderer,"Vida: "+handler.value(1)+"/30",8,13,0xb5ecd2,false);
        ctx.drawText(textRenderer,"Equipamentos",8,117,0x303030,false);
        String[] labels={"Cab","Peito","Pern","Pés","Mão","Extra"};
        for(int i=0;i<labels.length;i++)ctx.drawText(textRenderer,labels[i],8+i*27,146,0x404040,false);

    }
    @Override public void render(DrawContext ctx,int mouseX,int mouseY,float delta){
        renderBackground(ctx);super.render(ctx,mouseX,mouseY,delta);drawMouseoverTooltip(ctx,mouseX,mouseY);
    }
}
