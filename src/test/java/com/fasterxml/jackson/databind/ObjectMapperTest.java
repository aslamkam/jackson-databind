package com.fasterxml.jackson.databind;

import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.core.util.MinimalPrettyPrinter;
import com.fasterxml.jackson.databind.cfg.ConfigOverrides;
import com.fasterxml.jackson.databind.cfg.DeserializationContexts;
import com.fasterxml.jackson.databind.deser.DeserializerCache;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.introspect.VisibilityChecker;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.*;

import com.fasterxml.jackson.databind.JsonMappingException;

public class ObjectMapperTest extends BaseMapTest
{
	
	static class Users {
		public ArrayList<User> users;
		public Users(){
			users = new ArrayList<User>();
		}
		public ArrayList<User> getUsers() {
			return users;
		}
	
		public void addUsers(User user) {
			this.users.add(user);
		}
	
		public void setUsers(ArrayList<User> user) {
			this.users = user;
		}
	}

	static class User {
        public String name = null;
    }

    static class Bean {
        int value = 3;
        
        public void setX(int v) { value = v; }

        protected Bean() { }
        public Bean(int v) { value = v; }
    }

    static class EmptyBean { }

    @SuppressWarnings("serial")
    static class MyAnnotationIntrospector extends JacksonAnnotationIntrospector { }

    // for [databind#689]
    @SuppressWarnings("serial")
    static class FooPrettyPrinter extends MinimalPrettyPrinter {
        public FooPrettyPrinter() {
            super(" /*foo*/ ");
        }

        @Override
        public void writeArrayValueSeparator(JsonGenerator g) throws IOException
        {
            g.writeRaw(" , ");
        }
    }

    private final JsonMapper MAPPER = new JsonMapper();

    /*
    /**********************************************************
    /* Test methods, config
    /**********************************************************
     */

    public void testFeatureDefaults()
    {
        assertTrue(MAPPER.isEnabled(TokenStreamFactory.Feature.CANONICALIZE_FIELD_NAMES));
        assertTrue(MAPPER.isEnabled(JsonWriteFeature.QUOTE_FIELD_NAMES));
        assertTrue(MAPPER.isEnabled(StreamReadFeature.AUTO_CLOSE_SOURCE));
        assertTrue(MAPPER.isEnabled(StreamWriteFeature.AUTO_CLOSE_TARGET));
        assertFalse(MAPPER.isEnabled(JsonWriteFeature.ESCAPE_NON_ASCII));
        assertTrue(MAPPER.isEnabled(JsonWriteFeature.WRITE_NAN_AS_STRINGS));
        JsonMapper mapper = JsonMapper.builder()
                .disable(StreamWriteFeature.FLUSH_PASSED_TO_STREAM)
                .disable(JsonWriteFeature.WRITE_NAN_AS_STRINGS)
                .build();
        assertFalse(mapper.isEnabled(StreamWriteFeature.FLUSH_PASSED_TO_STREAM));
        assertFalse(mapper.isEnabled(JsonWriteFeature.WRITE_NAN_AS_STRINGS));
    }

    /*
    /**********************************************************
    /* Test methods, mapper.copy()
    /**********************************************************
     */

    // [databind#1580]
    public void testCopyOfConfigOverrides() throws Exception
    {
        ObjectMapper m = new ObjectMapper();
        SerializationConfig config = m.serializationConfig();
        assertEquals(ConfigOverrides.INCLUDE_ALL, config.getDefaultPropertyInclusion());
        assertEquals(JsonSetter.Value.empty(), config.getDefaultNullHandling());
        assertNull(config.getDefaultMergeable());

        // change
        VisibilityChecker customVis = VisibilityChecker.defaultInstance()
                .withFieldVisibility(Visibility.ANY);
        m = jsonMapperBuilder()
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_DEFAULT))
                .changeDefaultVisibility(vc -> customVis)
                .changeDefaultNullHandling(n -> n.withValueNulls(Nulls.SKIP))
                .defaultMergeable(Boolean.TRUE)
                .build();
    }

    /*
    /**********************************************************
    /* Test methods, other
    /**********************************************************
     */

    public void testProps()
    {
        // should have default factory
        assertNotNull(MAPPER.getNodeFactory());
        JsonNodeFactory nf = new JsonNodeFactory(true);
        JsonMapper m = JsonMapper.builder()
                .nodeFactory(nf)
                .build();
        assertNull(m.getInjectableValues());
        assertSame(nf, m.getNodeFactory());
    }

    // Test to ensure that we can check property ordering defaults...
    public void testConfigForPropertySorting() throws Exception
    {
        ObjectMapper m = new ObjectMapper();
        
        // sort-alphabetically is disabled by default:
        assertFalse(m.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY));
        SerializationConfig sc = m.serializationConfig();
        assertFalse(sc.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY));
        assertFalse(sc.shouldSortPropertiesAlphabetically());
        DeserializationConfig dc = m.deserializationConfig();
        assertFalse(dc.shouldSortPropertiesAlphabetically());

        // but when enabled, should be visible:
        m = jsonMapperBuilder()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .build();
        sc = m.serializationConfig();
        assertTrue(sc.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY));
        assertTrue(sc.shouldSortPropertiesAlphabetically());
        dc = m.deserializationConfig();
        // and not just via SerializationConfig, but also via DeserializationConfig
        assertTrue(dc.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY));
        assertTrue(dc.shouldSortPropertiesAlphabetically());
    }

	public void testTokenLocation() throws Exception   
    {
        ObjectMapper m = new ObjectMapper();
        
		final String JSON = String.join("\r\n",
		"{",
			"\"users\" : [",
				"{",
					"name : \"1\"",
				"}",
			"]",
		"}");

		try {
			Users users = m.readValue(JSON, Users.class);
		} catch(JsonMappingException e) {
			String x = (String) e.getMessage();
			System.out.println(x);
			int n = x.indexOf("line");
			assertEquals("line: 4", x.substring(n, n+7));
		}
    }

    public void testDeserializationContextCache() throws Exception   
    {
        ObjectMapper m = new ObjectMapper();
        final String JSON = "{ \"x\" : 3 }";

        DeserializationContexts.DefaultImpl dc = (DeserializationContexts.DefaultImpl) m._deserializationContexts;
        DeserializerCache cache = dc.cacheForTests();

        assertEquals(0, cache.cachedDeserializersCount());
        // and then should get one constructed for:
        Bean bean = m.readValue(JSON, Bean.class);
        assertNotNull(bean);
        // Since 2.6, serializer for int also cached:
        assertEquals(2, cache.cachedDeserializersCount());
        cache.flushCachedDeserializers();
        assertEquals(0, cache.cachedDeserializersCount());

        // 07-Nov-2014, tatu: As per [databind#604] verify that Maps also get cached
        m = new ObjectMapper();
        dc = (DeserializationContexts.DefaultImpl) m._deserializationContexts;
        cache = dc.cacheForTests();

        List<?> stuff = m.readValue("[ ]", List.class);
        assertNotNull(stuff);
        // may look odd, but due to "Untyped" deserializer thing, we actually have
        // 4 deserializers (int, List<?>, Map<?,?>, Object)
        assertEquals(4, cache.cachedDeserializersCount());
    }

    // For [databind#689]
    public void testCustomDefaultPrettyPrinter() throws Exception
    {
        final int[] input = new int[] { 1, 2 };

        JsonMapper m = new JsonMapper();

        // without anything else, compact:
        assertEquals("[1,2]", m.writeValueAsString(input));

        // or with default, get... defaults:
        m = JsonMapper.builder()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();
        assertEquals("[ 1, 2 ]", m.writeValueAsString(input));
        assertEquals("[ 1, 2 ]", m.writerWithDefaultPrettyPrinter().writeValueAsString(input));
        assertEquals("[ 1, 2 ]", m.writer().withDefaultPrettyPrinter().writeValueAsString(input));

        // but then with our custom thingy...
        m = JsonMapper.builder()
                .defaultPrettyPrinter(new FooPrettyPrinter())
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();
        assertEquals("[1 , 2]", m.writeValueAsString(input));
        assertEquals("[1 , 2]", m.writerWithDefaultPrettyPrinter().writeValueAsString(input));
        assertEquals("[1 , 2]", m.writer().withDefaultPrettyPrinter().writeValueAsString(input));

        // and yet, can disable too
        assertEquals("[1,2]", m.writer().without(SerializationFeature.INDENT_OUTPUT)
                .writeValueAsString(input));
    }

    public void testDataOutputViaMapper() throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectNode input = MAPPER.createObjectNode();
        input.put("a", 1);
        final String exp = "{\"a\":1}";
        try (DataOutputStream data = new DataOutputStream(bytes)) {
            MAPPER.writeValue((DataOutput) data, input);
        }
        assertEquals(exp, bytes.toString("UTF-8"));

        // and also via ObjectWriter...
        bytes.reset();
        try (DataOutputStream data = new DataOutputStream(bytes)) {
            MAPPER.writer().writeValue((DataOutput) data, input);
        }
        assertEquals(exp, bytes.toString("UTF-8"));
    }

    @SuppressWarnings("unchecked")
    public void testDataInputViaMapper() throws Exception
    {
        byte[] src = "{\"a\":1}".getBytes("UTF-8");
        DataInput input = new DataInputStream(new ByteArrayInputStream(src));
        Map<String,Object> map = (Map<String,Object>) MAPPER.readValue(input, Map.class);
        assertEquals(Integer.valueOf(1), map.get("a"));

        input = new DataInputStream(new ByteArrayInputStream(src));
        // and via ObjectReader
        map = MAPPER.readerFor(Map.class)
                .readValue(input);
        assertEquals(Integer.valueOf(1), map.get("a"));

        input = new DataInputStream(new ByteArrayInputStream(src));
        JsonNode n = MAPPER.readerFor(Map.class)
                .readTree(input);
        assertNotNull(n);
    }

    @SuppressWarnings("serial")
    public void testRegisterDependentModules() {

        final SimpleModule secondModule = new SimpleModule() {
            @Override
            public Object getRegistrationId() {
                return "dep1";
            }
        };

        final SimpleModule thirdModule = new SimpleModule() {
            @Override
            public Object getRegistrationId() {
                return "dep2";
            }
        };

        final SimpleModule mainModule = new SimpleModule() {
            @Override
            public Iterable<? extends Module> getDependencies() {
                return Arrays.asList(secondModule, thirdModule);
            }

            @Override
            public Object getRegistrationId() {
                return "main";
            }
        };

        ObjectMapper objectMapper = jsonMapperBuilder()
                .addModule(mainModule)
                .build();

        Collection<Module> mods = objectMapper.getRegisteredModules();
        List<Object> ids = mods.stream().map(mod -> mod.getRegistrationId())
                .collect(Collectors.toList());
        assertEquals(Arrays.asList("dep1", "dep2", "main"), ids);
    }
}
