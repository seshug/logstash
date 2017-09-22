package org.logstash.plugin;

import org.logstash.Event;
import org.logstash.common.parser.ConstructingObjectParser;
import org.logstash.common.parser.Field;
import org.logstash.common.parser.ObjectTransforms;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

class TranslateFilterPlugin {
    final static ConstructingObjectParser<TranslateFilter> TRANSLATE = new ConstructingObjectParser<TranslateFilter>(
            TranslateFilterPlugin::newFilter,
            Field.declareString("field"),

            // Things get a bit weird here. I can explain. The translate filter has two modes of operation:
            // One, where the user specifies a 'dictionary'. Two, where the user specifies 'dictionary_path'.
            // These two modes are mutually exclusive, so it makes sense to have a custom builder method to
            // determine which mode to use based on the given arguments. See TranslateFilter.newFilter.
            // It's unclear if this modality is a desirable feature, but I am only porting the translate filter
            // in this scenario as a a way to demonstrate and test the capabilities of ConstructingObjectParser.
            Field.declareMap("dictionary"),
            Field.declareField("dictionary_path", TranslateFilterPlugin::parsePath)
    );

    static {
        // These are all nice settings that are optional in the plugin.
        TRANSLATE.declareString("destination", TranslateFilter::setDestination);
        TRANSLATE.declareBoolean("exact", TranslateFilter::setExact);
        TRANSLATE.declareBoolean("override", TranslateFilter::setOverride);
        TRANSLATE.declareBoolean("regex", TranslateFilter::setRegex);
        TRANSLATE.declareString("fallback", TranslateFilter::setFallback);

        // Special handling of refresh_interval to reject when dictionary_path is not also set.
        TRANSLATE.declareInteger("refresh_interval", TranslateFilterPlugin::setRefreshInterval);
    }

    private static Path parsePath(Object input) {
        Path path = Paths.get(ObjectTransforms.transformString(input));

        if (!path.toFile().exists()) {
            throw new IllegalArgumentException("The given path does not exist: " + path);
        }

        return path;
    }

    private static TranslateFilter newFilter(String source, Map<String, Object> map, Path path) {
        if (map != null && path != null) {
            throw new IllegalArgumentException("You must specify either dictionary or dictionary_path, not both. Both are set.");
        }

        if (map != null) {
            // "dictionary" field was set, so args[1] is a map.
            return new TranslateFilter(source, map);
        } else {
            // dictionary_path set, so let's use a file-backed translate filter.
            return new FileBackedTranslateFilter(source, path);
        }
    }

    private static void setRefreshInterval(TranslateFilter filter, int refresh) {
        if (filter instanceof FileBackedTranslateFilter) {
            ((FileBackedTranslateFilter) filter).setRefreshInterval(refresh);
        } else {
            throw new IllegalArgumentException("refresh_interval is only valid when using dictionary_path.");
        }
    }

    // Processor will be defined in another PR, so this exists as a placeholder;
    interface Processor extends Function<Collection<Event>, Collection<Event>> {
    }

    public static class TranslateFilter implements Processor {
        protected Map<String, Object> map;
        private String source;
        private String target = "translation";
        private String fallback;
        private boolean exact = true;
        private boolean override = false;
        private boolean regex = false;

        TranslateFilter(String source, Map<String, Object> map, Path path) { /* ... */ }

        TranslateFilter(String source, Map<String, Object> map) {
            this(source);
            this.map = map;
        }

        TranslateFilter(String source) {
            this.source = source;
        }

        void setDestination(String target) {
            this.target = target;
        }

        private Object translate(Object input) {
            if (input instanceof String) {
                return map.get(input);
            } else {
                return null;
            }
        }

        @Override
        public Collection<Event> apply(Collection<Event> events) {
            // This implementation is incomplete. This test implementation primarily exists to demonstrate
            // the usage of the ConstructingObjectParser with a real plugin.
            for (Event event : events) {
                Object input = event.getField(source);
                if (input == null && fallback != null) {
                    event.setField(target, fallback);
                } else {
                    Object output = translate(input);
                    if (output != null) {
                        event.setField(target, output);
                    }
                }
            }
            return null;
        }

        void setExact(boolean exact) {
            this.exact = exact;
        }

        void setOverride(boolean flag) {
            this.override = flag;
        }

        void setFallback(String fallback) {
            this.fallback = fallback;
        }

        void setRegex(boolean flag) {
            this.regex = flag;

        }
    }

    public static class FileBackedTranslateFilter extends TranslateFilter {
        private final Path path;
        private int refresh;

        FileBackedTranslateFilter(String source, Path path) {
            super(source);
            this.path = path;
            // start a thread to read the Path and update the Map periodically.
            // implementation left as an exercise, since this is just a demo code :)
        }

        private void setRefreshInterval(int refresh) {
            this.refresh = refresh;

        }
    }
}
