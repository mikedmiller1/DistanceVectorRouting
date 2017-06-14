import java.io.IOException;

public class Router
{
    public static void main(String[] args) throws IOException
    {    	
    	System.out.println( "***** Router *****" );
    	
    	// Parse input arguments
    	String RouterName = args[ 0 ];
    	String LinkFilename = args[ 1 ];
    	int Port = Integer.parseInt( args[ 2 ] );
    	
    	// Create a new router and start it
    	RouterThread s = new RouterThread( RouterName, Port, LinkFilename );
        s.BroadcastInterval = 15000;
        s.start();
    }
}
