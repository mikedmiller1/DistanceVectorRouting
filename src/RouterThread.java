import java.io.*;
import java.net.*;
import java.util.*;

/**
 * @author Mike Miller
 *
 */
public class RouterThread extends Thread
{
	/*
	 * Properties
	 */
	protected DatagramSocket socket = null;
	protected String LinkFilename;
	protected BufferedReader LinkFileReader = null;
	protected boolean ServerRunning = true;
	protected ArrayList<Link> Links = new ArrayList<Link>();
	protected ArrayList<Route> Routes = new ArrayList<Route>();
	public int BroadcastInterval;
	protected Timer BroadcastTimer;
	protected int BroadcastNumber = 0;

	
	
	/*
	 * Default constructor
	 */
	public RouterThread() throws IOException
	{
		this( "", 0, "" );
	}

	
	
	/*
	 * Constructor
	 */
	public RouterThread( String name, int Port, String LinkFilename ) throws IOException
	{
		// Call the base Thread class constructor
		super( name );

		// Assign the link filename
		this.LinkFilename = LinkFilename;

		// Create a new UDP socket
		System.out.print("Starting UDP sender on port " + Port + "... ");
		socket = new DatagramSocket(Port);
		System.out.println("Done");

		// Read the link file
		ReadLinkFile();
	}

	
	
	public void run()
	{
		// Initialize the broadcast timer
		System.out.print("Starting " + BroadcastInterval + " ms broadcast timer... ");
		BroadcastTimer = new Timer();
		BroadcastTimer.scheduleAtFixedRate(
			new TimerTask()
			{
				@Override
				public void run()
				{
					try
					{
						Broadcast();
					}
					catch( IOException e )
					{
						e.printStackTrace();
					}
				}
			},
			BroadcastInterval,
			BroadcastInterval);
		System.out.println("Done");
		System.out.println( "" );
		
		while( ServerRunning )
		{
			try
			{
				// Initialize the data buffer
				byte[] ReceivedData = new byte[1500];

				// Receive the packet
				DatagramPacket packet = new DatagramPacket( ReceivedData, ReceivedData.length );
				socket.receive( packet );
				
				
				
			}

			catch( IOException e )
			{
				e.printStackTrace();
				//ServerRunning = false;
			}
		}
		socket.close();
	}

	
	
	/*
	 * Sends routing information to all the routers.
	 */
	protected void Broadcast() throws IOException
	{
		// Increment the broadcast number
		BroadcastNumber++;
		System.out.println("Broadcast number " + BroadcastNumber);
		
		// Serialize the list of routes
		String RoutesString = FileUtilities.toString( Links );
		
		
		// Loop through the routes
		System.out.println( "Sending to " + Links.size() + " nodes" );
		for( int RouteNum = 0; RouteNum < Links.size(); RouteNum++ )
		{
			// Get the current route
			Link CurrentRoute = Links.get( RouteNum );
			
			// Send the link list to the current router
			System.out.print( "Sending to " + CurrentRoute.Node.Name + " at " + CurrentRoute.Node.IpAddress + ":" + CurrentRoute.Node.Port + "... " );
			DatagramPacket packet = new DatagramPacket(
					RoutesString.getBytes(),
					RoutesString.length(),
					InetAddress.getByName( CurrentRoute.Node.IpAddress ),
					CurrentRoute.Node.Port );
			socket.send( packet );
			System.out.println( "Done" );
		}
		System.out.println( "Broadcast complete" );
		System.out.println( "" );
	}

	
	
	/*
	 * Reads the link file and updates the list of links.
	 */
	protected void ReadLinkFile() throws IOException
	{
		System.out.print("Updating links from file... ");

		// Clear the links list
		Links.clear();

		// Open the file reader
		try
		{
			LinkFileReader = new BufferedReader(new FileReader(LinkFilename));
		}

		catch( FileNotFoundException e )
		{
			System.err.println("Could not open link file.");
		}

		// Get the number of links
		int NumberOfLinks = Integer.parseInt(LinkFileReader.readLine());

		// Loop through the links
		for( int LinkNum = 0; LinkNum < NumberOfLinks; LinkNum++ )
		{
			// Create a new link and add it to the list
			Links.add(new Link(LinkFileReader.readLine()));
		}

		System.out.println("Done");
		System.out.println("Found " + NumberOfLinks + " links");
	}
}
