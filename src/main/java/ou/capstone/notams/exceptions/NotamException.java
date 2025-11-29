package ou.capstone.notams.exceptions;

import java.net.URISyntaxException;

public class NotamException extends Exception
{
    public NotamException( final Exception e )
    {
        super( e );
    }

    public NotamException( final String msg )
    {
        super( msg );
    }

    public NotamException( final String msg, final Exception e )
    {
        super( msg, e );
    }
}
