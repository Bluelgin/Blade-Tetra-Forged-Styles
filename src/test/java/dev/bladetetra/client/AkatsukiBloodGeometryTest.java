package dev.bladetetra.client;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AkatsukiBloodGeometryTest {
    @Test void continuationInheritsObliqueSteelRatherThanModelXAxis() {
        // A sloping blade whose centerline is deliberately not parallel to model X.
        var faces=new ArrayList<Vector3f[]>();
        for(int side=0;side<4;side++) {
            var f=new Vector3f[4];
            f[0]=corner(0,side);f[1]=corner(100,side);
            f[2]=corner(100,side+1);f[3]=corner(0,side+1);faces.add(f);
        }
        var frame=AkatsukiBloodGeometry.calibrate(faces).orElseThrow();
        assertEquals(.2F,frame.direction().y/frame.direction().x,.0001F);
        assertEquals(0,frame.direction().dot(frame.widthAxis()),.0001F);
        assertEquals(0,frame.direction().dot(frame.normal()),.0001F);
        var root=frame.point(50,-.14F,0,0);
        assertEquals(12,root.x,.001F);assertEquals(2.4F,root.y,.001F);
        for(float length:new float[]{1,10,50,100}) {
            var tip=frame.point(length,1,0,0);
            assertEquals(length,new Vector3f(tip).sub(frame.tip()).length(),.001F);
            assertEquals(.2F,(tip.y-frame.tip().y)/(tip.x-frame.tip().x),.0001F);
            assertEquals(tip,frame.point(length,1,0,2));
        }
    }
    @Test void unknownDegenerateOrNonfiniteGeometryDoesNotGuessAnAttachment() {
        assertTrue(AkatsukiBloodGeometry.calibrate(List.of()).isEmpty());
        assertTrue(AkatsukiBloodGeometry.calibrate(Collections.singletonList(new Vector3f[]{new Vector3f()})).isEmpty());
        assertTrue(AkatsukiBloodGeometry.calibrate(Collections.singletonList(new Vector3f[]{new Vector3f(Float.NaN,0,0)})).isEmpty());
    }
    private static Vector3f corner(float x,int side) {
        float y=x*.2F;return switch(side&3) {
            case 0 -> new Vector3f(x,y+2,.3F);
            case 1 -> new Vector3f(x,y-2,.3F);
            case 2 -> new Vector3f(x,y-2,-.3F);
            default -> new Vector3f(x,y+2,-.3F);
        };
    }
}
