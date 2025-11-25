package ou.capstone.notams.api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class QueryParamsTest
{
    @Test
    public void testPage1IsDefault(){
        final FaaNotamApiWrapper.QueryParamsBuilder qpb = new FaaNotamApiWrapper.QueryParamsBuilder( "KOKC" );
        assertTrue( qpb.build().contains("pageNum=1"), "QueryParamsBuilder did not initialize to the first page" );
    }

    @Test
    public void testAirportCorrect() {
        final String airportCode = "KOKC";
        final FaaNotamApiWrapper.QueryParamsBuilder qpb = new FaaNotamApiWrapper.QueryParamsBuilder( airportCode );
        assertTrue( qpb.build().contains("icaoLocation=" + airportCode), "QueryParamsBuilder did not use correct airport" );
    }

    @Test
    public void testPageSize() {
        final int pageSize = 999;
        final FaaNotamApiWrapper.QueryParamsBuilder qpb = new FaaNotamApiWrapper.QueryParamsBuilder( "KOKC" ).pageSize(999);
        assertTrue( qpb.build().contains("pageSize=" + pageSize), "QueryParamsBuilder did not use correct page size" );
    }

    @Test
    public void testPageNum() {
        final int pageNum = 999;
        final FaaNotamApiWrapper.QueryParamsBuilder qpb = new FaaNotamApiWrapper.QueryParamsBuilder( "KOKC" ).pageNum(999);
        assertTrue( qpb.build().contains("pageNum=" + pageNum), "QueryParamsBuilder did not use correct page size" );
    }
}
