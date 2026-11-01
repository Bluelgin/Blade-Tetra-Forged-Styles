package dev.bladetetra.client;

import org.joml.Vector3f;
import java.util.List;
import java.util.Optional;

/** Blade-local calibration from two steel cross-sections, excluding the beveled tip. */
final class AkatsukiBloodGeometry {
    record Frame(Vector3f tip,Vector3f grip,Vector3f direction,Vector3f widthAxis,Vector3f normal,
                 float width,float thickness,float overlap) {
        Vector3f point(float length,float progress,float widthScale,int side) {
            float along=progress<0?progress/.14F*overlap:progress*length;
            var center=new Vector3f(direction).mul(along).add(tip);
            return switch(side&3) {
                case 0 -> center.fma(width*widthScale,widthAxis);
                case 1 -> center.fma(thickness*widthScale,normal);
                case 2 -> center.fma(-width*widthScale,widthAxis);
                default -> center.fma(-thickness*widthScale,normal);
            };
        }
    }
    private record Section(Vector3f center,float width,float thickness) {}
    static Optional<Frame> calibrate(List<Vector3f[]> faces) {
        float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;
        for(var face:faces)for(var v:face) {
            if(!v.isFinite())return Optional.empty();min=Math.min(min,v.x);max=Math.max(max,v.x);
        }
        float span=max-min;if(!Float.isFinite(span)||span<.001F)return Optional.empty();
        Section neck=section(faces,min+span*.12F),shoulder=section(faces,min+span*.28F),grip=section(faces,max-span*.02F);
        if(neck==null||shoulder==null||grip==null||neck.width<.0001F)return Optional.empty();
        Vector3f direction=new Vector3f(neck.center).sub(shoulder.center).normalize();
        if(!direction.isFinite()||direction.x>-.5F)return Optional.empty();
        Vector3f tip=new Vector3f(direction).mul((min-neck.center.x)/direction.x).add(neck.center);
        Vector3f width=new Vector3f(0,1,0).fma(-direction.y,direction).normalize();
        Vector3f normal=new Vector3f(direction).cross(width).normalize();
        return Optional.of(new Frame(tip,grip.center,direction,width,normal,neck.width,
                Math.max(neck.thickness,neck.width*.08F),span*.12F/-direction.x));
    }
    private static Section section(List<Vector3f[]> faces,float x) {
        float loY=Float.POSITIVE_INFINITY,hiY=Float.NEGATIVE_INFINITY,loZ=loY,hiZ=hiY;
        for(var face:faces)for(int i=0;i<face.length;i++) {
            var a=face[i];var b=face[(i+1)%face.length];
            if(x<Math.min(a.x,b.x)||x>Math.max(a.x,b.x)||Math.abs(b.x-a.x)<.00001F)continue;
            float t=(x-a.x)/(b.x-a.x),y=a.y+(b.y-a.y)*t,z=a.z+(b.z-a.z)*t;
            loY=Math.min(loY,y);hiY=Math.max(hiY,y);loZ=Math.min(loZ,z);hiZ=Math.max(hiZ,z);
        }
        return !Float.isFinite(loY)?null:new Section(new Vector3f(x,(loY+hiY)/2,(loZ+hiZ)/2),(hiY-loY)/2,(hiZ-loZ)/2);
    }
    private AkatsukiBloodGeometry() {}
}
