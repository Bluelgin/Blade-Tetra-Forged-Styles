package dev.bladetetra.client;

import dev.bladetetra.visual.MaterialAppearance.TsubaProfile;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.TextureCoordinate;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Vertex;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Authored guard meshes in the existing mount, independent of material/gameplay data. */
final class ForgedGuardModel {
    private static final Map<Key, WavefrontObject> CACHE = new LinkedHashMap<>();
    private static final int SEGMENTS = 16;
    private record Key(WavefrontObject source, TsubaProfile profile) {}
    private ForgedGuardModel() {}

    static synchronized WavefrontObject forProfile(WavefrontObject source, TsubaProfile profile) {
        Key key = new Key(source, profile);
        if (CACHE.containsKey(key)) return CACHE.get(key);
        Map<String, List<Face>> groups = new LinkedHashMap<>();
        boolean changed = false;
        for (var group : source.groupObjects) {
            List<Face> replacement = replace(group.faces, profile, InventoryBladeModel.isIcon(group.name));
            groups.put(group.name, replacement);
            changed |= replacement != group.faces;
        }
        WavefrontObject result = changed ? RuntimeWavefrontViewFactory.create(groups) : source;
        CACHE.put(key, result);
        if (CACHE.size() > 128) CACHE.remove(CACHE.keySet().iterator().next());
        return result;
    }
    static synchronized void clear() { CACHE.clear(); }

    static List<Face> replace(List<Face> source, TsubaProfile profile, boolean icon) {
        List<Face> oldGuard = source.stream().filter(InventoryGuardGeometry::isGuard).toList();
        if (oldGuard.isEmpty()) return source;
        List<Face> result = new ArrayList<>(source.stream().filter(f -> !InventoryGuardGeometry.isGuard(f)).toList());
        if (profile == TsubaProfile.NONE) return result;
        List<Vec> grip = points(source.stream().filter(ForgedGuardModel::isGrip).toList());
        List<Vec> guard = points(oldGuard);
        if (grip.isEmpty()) return source; // Unsupported external geometry is left intact.
        Vec center = average(guard);
        Vec normal = average(grip).minus(center).unit();
        if (normal.length() < .5) return source;
        Vec tangent = new Vec(-normal.y, normal.x, 0).unit();
        if (tangent.length() < .5) tangent = new Vec(1, 0, 0);
        Vec across = normal.cross(tangent).unit();
        double radius = 0;
        for (Vec point : guard) {
            Vec delta = point.minus(center);
            radius = Math.max(radius, Math.hypot(delta.dot(tangent), delta.dot(across)));
        }
        if (radius < .001) return source;
        radius *= profile == TsubaProfile.MOKKO ? .82 : .89;
        // Inventory presentation exposes the face slightly; the physical world mount stays perpendicular.
        if (icon) {
            normal = normal.plus(new Vec(0, 0, .38)).unit();
            across = normal.cross(tangent).unit();
        }
        Frame frame = new Frame(center, tangent, across, normal, radius, radius * .055);
        double[] rings = profile==TsubaProfile.MOKKO ? new double[]{.19,.40,.72,1} : new double[]{.19,.88,1};
        boolean[][] cells = new boolean[rings.length-1][SEGMENTS];
        for (int ring=0; ring<cells.length; ring++) for (int sector=0; sector<SEGMENTS; sector++) {
            cells[ring][sector] = !(profile == TsubaProfile.MOKKO && ring == 1 && window(sector));
        }
        for (int ring=0; ring<cells.length; ring++) for (int sector=0; sector<SEGMENTS; sector++) {
            if (!cells[ring][sector]) continue;
            double shift=profile==TsubaProfile.MOKKO ? -.5 : 0;
            double a0=(sector+shift)*Math.PI*2/SEGMENTS, a1=(sector+1+shift)*Math.PI*2/SEGMENTS;
            Node p0=node(frame,profile,rings[ring],a0), p1=node(frame,profile,rings[ring+1],a0);
            Node p2=node(frame,profile,rings[ring+1],a1), p3=node(frame,profile,rings[ring],a1);
            add(result, p0, p1, p2, p3, 1);
            add(result, p3, p2, p1, p0, -1);
            // Close the outer bevel, blade slot and actual pierced windows, not alpha-only holes.
            if (ring==0 || !cells[ring-1][sector]) wall(result,p3,p0);
            if (ring==cells.length-1 || !cells[ring+1][sector]) wall(result,p1,p2);
            if (!cells[ring][Math.floorMod(sector-1,SEGMENTS)]) wall(result,p0,p1);
            if (!cells[ring][(sector+1)%SEGMENTS]) wall(result,p2,p3);
        }
        return result;
    }

    private static boolean window(int sector) {
        double angle=sector*Math.PI*2/SEGMENTS;
        double phase=Math.IEEEremainder(angle-Math.PI/4,Math.PI/2);
        return Math.abs(phase)<.16;
    }
    static double outline(TsubaProfile profile, double angle) {
        double c=Math.abs(Math.cos(angle)), s=Math.abs(Math.sin(angle));
        return switch(profile) {
            case MARU -> .94-.06*Math.cos(angle*2);
            case MOKKO -> .89+.11*Math.cos(angle*4);
            case KAKU -> Math.min(.96/Math.max(c,s),1.11/(c+s));
            case NONE -> 0;
        };
    }
    private static Node node(Frame f,TsubaProfile profile,double ring,double angle) {
        double shape=outline(profile,angle), u=Math.cos(angle)*ring*shape, v=Math.sin(angle)*ring*shape;
        if (ring==.19) {u=Math.cos(angle)*.18; v=Math.sin(angle)*.12;}
        Vec plane=f.center.plus(f.tangent.scale(u*f.radius)).plus(f.across.scale(v*f.radius));
        double height=f.thickness*(ring==1 ? .42 : 1);
        TextureCoordinate uv=new TextureCoordinate((float)((64+u*11.4)/128),(float)((70+v*11.4)/128));
        return new Node(plane.plus(f.normal.scale(height)),plane.minus(f.normal.scale(height)),uv);
    }
    private static void add(List<Face> faces,Node a,Node b,Node c,Node d,int side) {
        Face face=new Face();
        face.vertices=new Vertex[]{a.position(side),b.position(side),c.position(side),d.position(side)};
        face.textureCoordinates=new TextureCoordinate[]{a.uv,b.uv,c.uv,d.uv};
        face.faceNormal=face.calculateFaceNormal();
        faces.add(face);
    }
    private static void wall(List<Face> faces,Node a,Node b) {
        Face face=new Face();
        face.vertices=new Vertex[]{a.position(1),a.position(-1),b.position(-1),b.position(1)};
        face.textureCoordinates=new TextureCoordinate[]{a.uv,a.uv,b.uv,b.uv};
        face.faceNormal=face.calculateFaceNormal(); faces.add(face);
    }
    private static boolean isGrip(Face face) {
        if(face.textureCoordinates==null || face.textureCoordinates.length==0) return false;
        for(var uv:face.textureCoordinates) if(uv.u*128<1 || uv.u*128>=47 || uv.v*128<59 || uv.v*128>=81) return false;
        return true;
    }
    private static List<Vec> points(List<Face> faces) {
        Map<String,Vec> points=new LinkedHashMap<>();
        for(Face face:faces) for(Vertex v:face.vertices) points.putIfAbsent(v.x+":"+v.y+":"+v.z,new Vec(v.x,v.y,v.z));
        return new ArrayList<>(points.values());
    }
    private static Vec average(List<Vec> points) {
        Vec sum=new Vec(0,0,0); for(Vec point:points) sum=sum.plus(point); return sum.scale(1.0/points.size());
    }
    private record Frame(Vec center,Vec tangent,Vec across,Vec normal,double radius,double thickness) {}
    private record Node(Vec front,Vec back,TextureCoordinate uv) {
        Vertex position(int side) {Vec p=side>0?front:back; return new Vertex((float)p.x,(float)p.y,(float)p.z);}
    }
    private record Vec(double x,double y,double z) {
        Vec plus(Vec b) {return new Vec(x+b.x,y+b.y,z+b.z);}
        Vec minus(Vec b) {return new Vec(x-b.x,y-b.y,z-b.z);}
        Vec scale(double s) {return new Vec(x*s,y*s,z*s);}
        double dot(Vec b) {return x*b.x+y*b.y+z*b.z;}
        double length() {return Math.sqrt(dot(this));}
        Vec unit() {double length=length(); return length<1e-8?this:scale(1/length);}
        Vec cross(Vec b) {return new Vec(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x);}
    }
}
