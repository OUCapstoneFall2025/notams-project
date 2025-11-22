package ou.capstone.notams.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import ou.capstone.notams.Notam;

public class FaaNotamApiWrapperTest
{
	@InjectMocks
	NotamFetcher fetcher;

	@BeforeEach
	public void setup() throws Exception
	{
		MockitoAnnotations.openMocks( this );
	}

	@Test
	public void fetchAllPages() throws Exception
	{
        try (MockedStatic<FaaNotamApiWrapper> wrapper = mockStatic(
                FaaNotamApiWrapper.class )) {
            wrapper.when( FaaNotamApiWrapper::validateCredentials )
                    .thenAnswer( invocation -> null );

            wrapper.when( () -> FaaNotamApiWrapper.fetchAllPages( ArgumentMatchers.any() )).thenCallRealMethod();

            wrapper.when( () -> FaaNotamApiWrapper.fetchRawJson( ArgumentMatchers.any() ) )
                    .thenReturn( Files.readString(
                            Path.of( "src/test/resources/pages/okc-page-1.json" ) ) )
                    .thenReturn( Files.readString( Path.of(
                            "src/test/resources/pages/okc-page-2.json" ) ) );

            List<Notam> results = this.fetcher.fetchForAirport( "KOKC" );
            assertEquals( 13, results.size() );
        }
	}

    @Test
    public void fetchNoNotams() throws Exception
    {
        try (MockedStatic<FaaNotamApiWrapper> wrapper = mockStatic(
                FaaNotamApiWrapper.class )) {
            wrapper.when( FaaNotamApiWrapper::validateCredentials )
                    .thenAnswer( invocation -> null );

            wrapper.when( () -> FaaNotamApiWrapper.fetchAllPages( ArgumentMatchers.any() )).thenCallRealMethod();

            // No NOTAMs included in this sample file
            wrapper.when( () -> FaaNotamApiWrapper.fetchRawJson( ArgumentMatchers.any() ) )
                    .thenReturn( Files.readString(
                            Path.of( "src/test/resources/no-items.json" ) ) );

            List<Notam> results = this.fetcher.fetchForAirport( "KOKC" );
            assertEquals( 0, results.size() );
        }
    }

}
