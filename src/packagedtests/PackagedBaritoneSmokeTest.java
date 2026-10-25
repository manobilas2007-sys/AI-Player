import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

/** Execute the released library from the playable JAR alone, not Gradle outputs. */
public final class PackagedBaritoneSmokeTest {
    private static void require(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Expected the distributable JAR path");
        Path artifact=Path.of(args[0]).toRealPath();
        // The platform parent exposes only the JDK, never the development module.
        try(URLClassLoader isolated=new URLClassLoader(new URL[]{artifact.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            Class<?> goal;
            try {
                goal=Class.forName("dev.aiplayer.baritone.Goal",true,isolated);
            } catch(ClassNotFoundException missing) {
                NoClassDefFoundError failure=new NoClassDefFoundError(
                    "dev/aiplayer/baritone/Goal is not loadable from the distributed AI Player JAR");
                failure.initCause(missing);throw failure;
            }
            require(goal.getClassLoader()==isolated,"Goal leaked from a parent/development classpath");
            require(Path.of(goal.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath()
                .equals(artifact),"Goal did not come from the distributed artifact");
            List<String> names=new ArrayList<>();
            try(JarFile released=new JarFile(artifact.toFile())) {
                released.stream().filter(entry->entry.getName().startsWith("dev/aiplayer/baritone/")
                    && entry.getName().endsWith(".class"))
                    .forEach(entry->names.add(entry.getName().substring(0,entry.getName().length()-6).replace('/','.')));
            }
            require(!names.isEmpty(),"No library classes in released JAR");
            for(String name:names) {
                Class<?> type=Class.forName(name,true,isolated);
                require(type.getClassLoader()==isolated,"Class leaked from another loader: "+name);
            }
            Class<?> position=isolated.loadClass("dev.aiplayer.baritone.Position");
            Class<?> step=isolated.loadClass("dev.aiplayer.baritone.Step");
            Class<?> movement=isolated.loadClass("dev.aiplayer.baritone.Movement");
            Class<?> context=isolated.loadClass("dev.aiplayer.baritone.SearchContext");
            Class<?> limits=isolated.loadClass("dev.aiplayer.baritone.SearchLimits");
            Class<?> finder=isolated.loadClass("dev.aiplayer.baritone.AStarPathFinder");
            Class<?> result=isolated.loadClass("dev.aiplayer.baritone.SearchResult");
            Object traverse=null;
            for(Object constant:movement.getEnumConstants())if(constant.toString().equals("TRAVERSE"))traverse=constant;
            require(traverse!=null,"Missing TRAVERSE movement");
            Object start=position.getConstructor(int.class,int.class,int.class).newInstance(0,0,0);
            Object middle=position.getConstructor(int.class,int.class,int.class).newInstance(1,0,0);
            Object destination=position.getConstructor(int.class,int.class,int.class).newInstance(2,0,0);
            Object direct=step.getConstructor(position,double.class,movement).newInstance(destination,50.0,traverse);
            Object cheaperFirst=step.getConstructor(position,double.class,movement).newInstance(middle,5.0,traverse);
            Object cheaperLast=step.getConstructor(position,double.class,movement).newInstance(destination,5.0,traverse);
            Object graph=Proxy.newProxyInstance(isolated,new Class<?>[]{context},(proxy,method,arguments)->{
                if(method.getDeclaringClass()==Object.class)return switch(method.getName()) {
                    case "toString" -> "released-artifact-search-context";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy==arguments[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                };
                if(!method.getName().equals("neighbors"))throw new UnsupportedOperationException(method.getName());
                if(arguments[0].equals(start))return List.of(direct,cheaperFirst);
                if(arguments[0].equals(middle))return List.of(cheaperLast);
                return List.of();
            });
            Object target=isolated.loadClass("dev.aiplayer.baritone.GoalBlock")
                .getConstructor(position).newInstance(destination);
            Object budget=limits.getConstructor(int.class,int.class).newInstance(64,8);
            Object planner=finder.getConstructor().newInstance();
            Object route=finder.getMethod("search",position,goal,context,limits)
                .invoke(planner,start,target,graph,budget);
            require(result.getMethod("status").invoke(route).toString().equals("COMPLETE"),"Real search did not complete");
            require((boolean)result.getMethod("complete").invoke(route),"Complete flag disagrees with status");
            List<?> steps=(List<?>)result.getMethod("steps").invoke(route);
            require(steps.size()==2,"Released heap did not choose the cheaper two-step route");
            require(step.getMethod("destination").invoke(steps.get(0)).equals(middle),"Wrong cheaper route");
            require(step.getMethod("destination").invoke(steps.get(1)).equals(destination),"Wrong destination");
            require(Math.abs((double)result.getMethod("cost").invoke(route)-10.0)<1e-9,"Wrong path cost");
            Object tiny=limits.getConstructor(int.class,int.class).newInstance(1,8);
            Object incomplete=finder.getMethod("search",position,goal,context,limits)
                .invoke(planner,start,target,graph,tiny);
            require(!(boolean)result.getMethod("complete").invoke(incomplete),"Node limit falsely authorized execution");
            Object goals=Array.newInstance(goal,1);Array.set(goals,0,target);
            Object composite=isolated.loadClass("dev.aiplayer.baritone.GoalComposite")
                .getConstructor(goals.getClass()).newInstance(goals);
            require((boolean)goal.getMethod("isInGoal",int.class,int.class,int.class)
                .invoke(composite,2,0,0),"Released composite goal failed");
            System.out.println("Packaged Baritone smoke passed: "+names.size()+" classes loaded only from "
                +artifact.getFileName()+"; real search COMPLETE (2 steps, cost 10), node limit enforced.");
        }
    }
}
