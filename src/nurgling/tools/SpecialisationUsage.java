package nurgling.tools;

import nurgling.actions.bots.registry.BotDescriptor;
import nurgling.actions.bots.registry.BotRegistry;
import nurgling.areas.NContext;
import nurgling.widgets.Specialisation;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Discovers which registered bots use each area specialisation. */
public class SpecialisationUsage {
    private static final String SPECNAME_CLASS = "nurgling/widgets/Specialisation$SpecName";
    private static final String NCONTEXT_CLASS = "nurgling/areas/NContext";
    private static final String WORKSTATION_MAP = "workstation_spec_map";

    private static volatile Map<String, List<String>> usage = null;
    private static Thread scanner = null;

    public static synchronized void request() {
        if(usage != null || scanner != null)
            return;
        scanner = new Thread(() -> {
            Map<String, List<String>> result = scan();
            synchronized(SpecialisationUsage.class) {
                usage = result;
                scanner = null;
            }
        }, "Specialisation usage scan");
        scanner.setDaemon(true);
        scanner.start();
    }

    /** Returns null while scanning, otherwise the sorted bot names for the specialisation. */
    public static List<String> botsFor(String spec) {
        Map<String, List<String>> current = usage;
        if(current == null)
            return null;
        return current.getOrDefault(spec, Collections.emptyList());
    }

    static Map<String, List<String>> scan() {
        Map<String, ClassInfo> cache = new HashMap<>();
        Map<String, TreeSet<String>> found = new HashMap<>();
        for(BotDescriptor bot : BotRegistry.all()) {
            String name = bot.getDisplayName();
            if(name == null || name.trim().isEmpty())
                continue;
            for(String spec : specsOf(bot.clazz.getName().replace('.', '/'), cache))
                found.computeIfAbsent(spec, ignored -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)).add(name);
        }

        Map<String, List<String>> result = new HashMap<>();
        for(Map.Entry<String, TreeSet<String>> entry : found.entrySet())
            result.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        return result;
    }

    private static Set<String> specsOf(String root, Map<String, ClassInfo> cache) {
        Set<String> specs = new HashSet<>();
        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        seen.add(root);
        queue.add(root);
        while(!queue.isEmpty()) {
            ClassInfo info = info(queue.poll(), cache);
            specs.addAll(info.specs);
            if(info.workstations)
                specs.addAll(workstationSpecs());
            for(String ref : info.refs) {
                if(traversable(ref) && seen.add(ref))
                    queue.add(ref);
            }
        }
        return specs;
    }

    private static boolean traversable(String cls) {
        if(cls.startsWith("nurgling/actions/bots/registry/"))
            return false;
        return cls.startsWith("nurgling/actions/") || cls.startsWith("nurgling/bots/")
                || cls.startsWith("nurgling/conf/");
    }

    private static Set<String> workstationSpecs = null;

    private static synchronized Set<String> workstationSpecs() {
        if(workstationSpecs == null) {
            Set<String> specs = new HashSet<>();
            for(Specialisation.SpecName spec : NContext.workstation_spec_map.values())
                specs.add(spec.toString());
            workstationSpecs = specs;
        }
        return workstationSpecs;
    }

    private static ClassInfo info(String cls, Map<String, ClassInfo> cache) {
        ClassInfo info = cache.get(cls);
        if(info == null) {
            info = parse(cls);
            if(info == null)
                info = new ClassInfo();
            cache.put(cls, info);
        }
        return info;
    }

    private static class ClassInfo {
        final Set<String> specs = new HashSet<>();
        final Set<String> refs = new HashSet<>();
        boolean workstations = false;
    }

    private static final int CP_UTF8 = 1;
    private static final int CP_INT = 3;
    private static final int CP_FLOAT = 4;
    private static final int CP_LONG = 5;
    private static final int CP_DOUBLE = 6;
    private static final int CP_CLASS = 7;
    private static final int CP_STRING = 8;
    private static final int CP_FIELD = 9;
    private static final int CP_METHOD = 10;
    private static final int CP_INTERFACE_METHOD = 11;
    private static final int CP_NAME_TYPE = 12;
    private static final int CP_METHOD_HANDLE = 15;
    private static final int CP_METHOD_TYPE = 16;
    private static final int CP_DYNAMIC = 17;
    private static final int CP_INVOKE_DYNAMIC = 18;
    private static final int CP_MODULE = 19;
    private static final int CP_PACKAGE = 20;

    /** Reads the constant pool entries needed for specialisation and class references. */
    private static ClassInfo parse(String cls) {
        try(InputStream resource = SpecialisationUsage.class.getClassLoader().getResourceAsStream(cls + ".class")) {
            if(resource == null)
                return null;
            DataInputStream input = new DataInputStream(new BufferedInputStream(resource));
            if(input.readInt() != 0xcafebabe)
                return null;
            input.readUnsignedShort();
            input.readUnsignedShort();
            int count = input.readUnsignedShort();
            int[] tag = new int[count];
            int[] first = new int[count];
            int[] second = new int[count];
            String[] utf = new String[count];

            for(int index = 1; index < count; index++) {
                int currentTag = tag[index] = input.readUnsignedByte();
                switch(currentTag) {
                    case CP_UTF8:
                        utf[index] = input.readUTF();
                        break;
                    case CP_CLASS:
                    case CP_STRING:
                    case CP_METHOD_TYPE:
                    case CP_MODULE:
                    case CP_PACKAGE:
                        first[index] = input.readUnsignedShort();
                        break;
                    case CP_METHOD_HANDLE:
                        first[index] = input.readUnsignedByte();
                        second[index] = input.readUnsignedShort();
                        break;
                    case CP_FIELD:
                    case CP_METHOD:
                    case CP_INTERFACE_METHOD:
                    case CP_NAME_TYPE:
                    case CP_DYNAMIC:
                    case CP_INVOKE_DYNAMIC:
                        first[index] = input.readUnsignedShort();
                        second[index] = input.readUnsignedShort();
                        break;
                    case CP_INT:
                    case CP_FLOAT:
                        input.readInt();
                        break;
                    case CP_LONG:
                    case CP_DOUBLE:
                        input.readLong();
                        index++;
                        break;
                    default:
                        return null;
                }
            }

            ClassInfo info = new ClassInfo();
            for(int index = 1; index < count; index++) {
                if(tag[index] == CP_CLASS) {
                    String name = element(stringAt(utf, tag, first[index], CP_UTF8));
                    if(name != null)
                        info.refs.add(name);
                } else if(tag[index] == CP_FIELD) {
                    String owner = className(utf, tag, first, first[index]);
                    String field = fieldName(utf, tag, first, second[index]);
                    if(owner == null || field == null)
                        continue;
                    if(owner.equals(SPECNAME_CLASS))
                        info.specs.add(field);
                    else if(owner.equals(NCONTEXT_CLASS) && field.equals(WORKSTATION_MAP))
                        info.workstations = true;
                }
            }
            return info;
        } catch(IOException exception) {
            return null;
        }
    }

    private static String stringAt(String[] utf, int[] tag, int index, int expectedTag) {
        if(index < 1 || index >= tag.length || tag[index] != expectedTag)
            return null;
        return utf[index];
    }

    private static String className(String[] utf, int[] tag, int[] first, int index) {
        if(index < 1 || index >= tag.length || tag[index] != CP_CLASS)
            return null;
        return stringAt(utf, tag, first[index], CP_UTF8);
    }

    private static String fieldName(String[] utf, int[] tag, int[] first, int index) {
        if(index < 1 || index >= tag.length || tag[index] != CP_NAME_TYPE)
            return null;
        return stringAt(utf, tag, first[index], CP_UTF8);
    }

    private static String element(String name) {
        if(name == null)
            return null;
        int dimensions = 0;
        while(dimensions < name.length() && name.charAt(dimensions) == '[')
            dimensions++;
        if(dimensions == 0)
            return name;
        if(dimensions < name.length() && name.charAt(dimensions) == 'L' && name.endsWith(";"))
            return name.substring(dimensions + 1, name.length() - 1);
        return null;
    }
}
