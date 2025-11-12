package ou.capstone.notams;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.apache.commons.cli.ParseException;
import org.junit.jupiter.api.Test;

public class AppArgParsingTest
{
    @Test
    public void testMissingDestinationThrowsException()
    {
        assertThrows( ParseException.class, () -> {
            // only departure airport is provided, destination is missing
            final String[] params = { "--departure", "okc" };
            App.main( params );
        } );
    }

    @Test
    public void testMissingDestinationShortThrowsException()
    {
        assertThrows( ParseException.class, () -> {
            final String[] params = { "-d", "okc" };
            App.main( params );
        } );
    }

    @Test
    public void testMissingDepartureThrowsException()
    {
        assertThrows( ParseException.class, () -> {
            // only destination airport is provided, departure is missing
            final String[] params = { "--destination", "okc" };
            App.main( params );
        } );
    }

    @Test
    public void testMissingDepartureShortThrowsException()
    {
        assertThrows( ParseException.class, () -> {
            final String[] params = { "-e", "okc" };
            App.main( params );
        } );
    }

    @Test
    public void testMissingDepartureArgThrowsException()
    {
        assertThrows( ParseException.class, () -> {
            final String[] params = { "--departure" };
            App.main( params );
        } );
    }

    @Test
    public void testMissingDestinationArgThrowsException()
    {
        assertThrows( ParseException.class, () -> {
            final String[] params = { "--destination" };
            App.main( params );
        } );
    }

    @Test
    public void testNoArgs() throws Exception
    {
        App.ExitHandler exitHandler = mock( App.ExitHandler.class );
        App.setExitHandler( exitHandler );
        final String[] params = {};
        App.main( params );
        // Should just print help and exit with return code of zero
        verify( exitHandler ).exit( 0 );
    }

    @Test
    public void testHelp() throws Exception
    {
        App.ExitHandler exitHandler = mock( App.ExitHandler.class );
        App.setExitHandler( exitHandler );
        final String[] params = { "--help" };
        App.main( params );
        verify( exitHandler ).exit( 0 );
    }

    @Test
    public void testUnsupportedOptions()
    {
        assertThrows( ParseException.class, () -> {
            final String[] params = { "--clowns", "all-of-them" };
            App.main( params );
        } );
    }
}
