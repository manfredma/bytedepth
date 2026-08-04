package manfred.bytedepth.infrastructure.stats;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.City;
import com.maxmind.geoip2.record.Country;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

class GeoIpServiceTest {

    @Test
    void missingOrBlankDatabaseConfigurationSafelyDisablesLookup() {
        GeoIpService blank = new GeoIpService(" ");
        blank.init();
        GeoIpService unset = new GeoIpService(null);
        unset.init();

        assertEquals(GeoInfo.unknown(), blank.resolve("127.0.0.1"));
        assertEquals(GeoInfo.unknown(), new GeoIpService("/definitely/not/present.mmdb").resolve(""));
        assertEquals(GeoInfo.unknown(), unset.resolve(null));
    }

    @Test
    void geoInfoUnknownUsesEmptyLocation() {
        GeoInfo unknown = GeoInfo.unknown();

        assertEquals("", unknown.country());
        assertEquals("", unknown.city());
    }

    @Test
    @DisplayName("init() logs warning when database file does not exist")
    void init_fileNotExists() {
        GeoIpService service = new GeoIpService("/nonexistent/path/to/db.mmdb");
        service.init();
        // reader stays null -> resolve returns unknown
        assertEquals(GeoInfo.unknown(), service.resolve("8.8.8.8"));
    }

    @Test
    @DisplayName("init() catches IOException when database fails to load")
    void init_catchesIOException() throws Exception {
        File tempFile = File.createTempFile("geoip-test-io", ".mmdb");
        tempFile.deleteOnExit();

        GeoIpService service = new GeoIpService(tempFile.getAbsolutePath());
        // init() will try to build a real DatabaseReader from the temp file.
        // The file is not a valid MaxMind DB, so it will throw an IOException
        // which is caught -> reader stays null -> resolve returns unknown
        service.init();

        assertEquals(GeoInfo.unknown(), service.resolve("8.8.8.8"));
    }

    @Test
    @DisplayName("init() retains a loaded MaxMind reader")
    void init_loadsExistingDatabaseReader() throws Exception {
        File database = File.createTempFile("geoip-test-valid", ".mmdb");
        DatabaseReader reader = mock(DatabaseReader.class);
        try (var ignored = mockConstruction(DatabaseReader.Builder.class,
                (builder, context) -> when(builder.build()).thenReturn(reader))) {
            GeoIpService service = new GeoIpService(database.getAbsolutePath());

            service.init();

            assertEquals(reader, readerFor(service));
        } finally {
            database.delete();
        }
    }

    @Test
    @DisplayName("resolve() returns unknown when reader is null")
    void resolve_readerNullReturnsUnknown() {
        GeoIpService service = new GeoIpService("");
        service.init();
        assertEquals(GeoInfo.unknown(), service.resolve("1.2.3.4"));
    }

    @Test
    @DisplayName("resolve() returns unknown when ip is null")
    void resolve_nullIpReturnsUnknown() throws Exception {
        GeoIpService service = new GeoIpService("");
        DatabaseReader mockReader = mock(DatabaseReader.class);
        setReader(service, mockReader);

        assertEquals(GeoInfo.unknown(), service.resolve(null));
    }

    @Test
    @DisplayName("resolve() returns unknown when ip is blank")
    void resolve_blankIpReturnsUnknown() throws Exception {
        GeoIpService service = new GeoIpService("");
        DatabaseReader mockReader = mock(DatabaseReader.class);
        setReader(service, mockReader);

        assertEquals(GeoInfo.unknown(), service.resolve("  "));
    }

    @Test
    @DisplayName("resolve() returns geo info with country and city")
    void resolve_returnsGeoInfo() throws Exception {
        GeoIpService service = new GeoIpService("/tmp/fake.mmdb");
        DatabaseReader mockReader = mock(DatabaseReader.class);
        CityResponse mockResponse = mock(CityResponse.class);
        Country mockCountry = mock(Country.class);
        City mockCity = mock(City.class);

        when(mockReader.city(any(InetAddress.class))).thenReturn(mockResponse);
        when(mockResponse.getCountry()).thenReturn(mockCountry);
        when(mockResponse.getCity()).thenReturn(mockCity);
        when(mockCountry.getName()).thenReturn("United States");
        when(mockCity.getName()).thenReturn("Mountain View");

        setReader(service, mockReader);

        GeoInfo result = service.resolve("8.8.8.8");
        assertEquals("United States", result.country());
        assertEquals("Mountain View", result.city());
    }

    @Test
    @DisplayName("resolve() returns empty country when country name is null")
    void resolve_nullCountryNameReturnsEmptyString() throws Exception {
        GeoIpService service = new GeoIpService("/tmp/fake.mmdb");
        DatabaseReader mockReader = mock(DatabaseReader.class);
        CityResponse mockResponse = mock(CityResponse.class);
        Country mockCountry = mock(Country.class);
        City mockCity = mock(City.class);

        when(mockReader.city(any(InetAddress.class))).thenReturn(mockResponse);
        when(mockResponse.getCountry()).thenReturn(mockCountry);
        when(mockResponse.getCity()).thenReturn(mockCity);
        when(mockCountry.getName()).thenReturn(null);
        when(mockCity.getName()).thenReturn("Some City");

        setReader(service, mockReader);

        GeoInfo result = service.resolve("8.8.8.8");
        assertEquals("", result.country());
        assertEquals("Some City", result.city());
    }

    @Test
    @DisplayName("resolve() returns empty city when city name is null")
    void resolve_nullCityNameReturnsEmptyString() throws Exception {
        GeoIpService service = new GeoIpService("/tmp/fake.mmdb");
        DatabaseReader mockReader = mock(DatabaseReader.class);
        CityResponse mockResponse = mock(CityResponse.class);
        Country mockCountry = mock(Country.class);
        City mockCity = mock(City.class);

        when(mockReader.city(any(InetAddress.class))).thenReturn(mockResponse);
        when(mockResponse.getCountry()).thenReturn(mockCountry);
        when(mockResponse.getCity()).thenReturn(mockCity);
        when(mockCountry.getName()).thenReturn("France");
        when(mockCity.getName()).thenReturn(null);

        setReader(service, mockReader);

        GeoInfo result = service.resolve("1.1.1.1");
        assertEquals("France", result.country());
        assertEquals("", result.city());
    }

    @Test
    @DisplayName("resolve() catches GeoIp2Exception and returns unknown")
    void resolve_geoIp2ExceptionReturnsUnknown() throws Exception {
        GeoIpService service = new GeoIpService("/tmp/fake.mmdb");
        DatabaseReader mockReader = mock(DatabaseReader.class);
        when(mockReader.city(any(InetAddress.class)))
                .thenThrow(new GeoIp2Exception("Not found"));

        setReader(service, mockReader);

        assertEquals(GeoInfo.unknown(), service.resolve("10.0.0.1"));
    }

    @Test
    @DisplayName("resolve() catches IOException and returns unknown")
    void resolve_ioExceptionReturnsUnknown() throws Exception {
        GeoIpService service = new GeoIpService("/tmp/fake.mmdb");
        DatabaseReader mockReader = mock(DatabaseReader.class);
        when(mockReader.city(any(InetAddress.class)))
                .thenThrow(new java.io.IOException("IO error"));

        setReader(service, mockReader);

        assertEquals(GeoInfo.unknown(), service.resolve("10.0.0.2"));
    }

    @Test
    @DisplayName("resolve() catches unexpected Exception and returns unknown")
    void resolve_unexpectedExceptionReturnsUnknown() throws Exception {
        GeoIpService service = new GeoIpService("/tmp/fake.mmdb");
        DatabaseReader mockReader = mock(DatabaseReader.class);
        when(mockReader.city(any(InetAddress.class)))
                .thenThrow(new RuntimeException("Unexpected"));

        setReader(service, mockReader);

        assertEquals(GeoInfo.unknown(), service.resolve("10.0.0.3"));
    }

    private static void setReader(GeoIpService service, DatabaseReader reader) throws Exception {
        readerField().set(service, reader);
    }

    private static DatabaseReader readerFor(GeoIpService service) throws Exception {
        return (DatabaseReader) readerField().get(service);
    }

    private static Field readerField() throws NoSuchFieldException {
        Field readerField = GeoIpService.class.getDeclaredField("reader");
        readerField.setAccessible(true);
        return readerField;
    }
}
