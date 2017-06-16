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
	protected Node ThisRouter;
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
	public RouterThread( String Name, int Port, String LinkFilename ) throws IOException
	{
		// Call the base Thread class constructor
		super( Name );

		// Assign the link filename
		this.LinkFilename = LinkFilename;
		
		// Create a new UDP socket
		System.out.print("Starting UDP sender on port " + Port + "... ");
		socket = new DatagramSocket(Port);
		System.out.println("Done");
		
		// Create the node for this router
		ThisRouter = new Node( Name, socket.getLocalAddress().toString(), Port );

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
				
				
				// Deserialize the packet data into a routes list
				@SuppressWarnings("unchecked")
				ArrayList<Route> RecevedRoutes = (ArrayList<Route>)FileUtilities.fromString( new String( packet.getData(), 0, packet.getLength() ) );
				
				// TODO: Perform distance vector algorithm to update routing list
				
			}

			catch( IOException | ClassNotFoundException e )
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
		
		// Read the link file
		ReadLinkFile();
		
		// Serialize the list of routes
		String RoutesString = FileUtilities.toString( Routes );
		
		
		// Loop through the links
		System.out.println( "Sending to " + Links.size() + " nodes" );
		for( int RouteNum = 0; RouteNum < Links.size(); RouteNum++ )
		{
			// Get the current link
			Link CurrentLink = Links.get( RouteNum );
			
			// Send the route list to the current link
			System.out.print( "Sending to " + CurrentLink.Node.Name + " at " + CurrentLink.Node.IpAddress + ":" + CurrentLink.Node.Port + "... " );
			DatagramPacket packet = new DatagramPacket(
					RoutesString.getBytes(),
					RoutesString.length(),
					InetAddress.getByName( CurrentLink.Node.IpAddress ),
					CurrentLink.Node.Port );
			socket.send( packet );
			System.out.println( "Done" );
		}
		System.out.println( "Broadcast complete" );
		System.out.println( "" );
	}

	
	
	/*
	 * Reads the link file and updates the list of links and routes.
	 */
	protected void ReadLinkFile() throws IOException
	{
		System.out.print("Updating links from file... ");

		// Clear the links list
		Links.clear();

		// Open the file reader
		try
		{
			LinkFileReader = new BufferedReader( new FileReader(LinkFilename) );
		}

		catch( FileNotFoundException e )
		{
			System.err.println("Could not open link file.");
		}

		
		// Get the number of links
		int NumberOfLinks = Integer.parseInt( LinkFileReader.readLine() );

		// Loop through the links
		for( int LinkNum = 0; LinkNum < NumberOfLinks; LinkNum++ )
		{
			// Create a new link and add it to the list of links
			Link NewLink = new Link( LinkFileReader.readLine() );
			Links.add( NewLink );
			
			// Check if there is already a route to the link
			Route CurrentRoute = FindRoute( NewLink.Node.Name );
			
			// If there is, update the route
			if( CurrentRoute != null )
			{
				CurrentRoute.Cost = NewLink.Cost;
			}
			
			// Otherwise, create a new route
			else
			{
				Routes.add( new Route( ThisRouter, NewLink.Node, NewLink.Node, NewLink.Cost ) );
			}
		}

		System.out.println("Done");
		System.out.println("Found " + NumberOfLinks + " links");
	}
	
	
	
	/*
	 * Returns the route to the specified destination router name.
	 * If no route is found, returns null.
	 */
	Route FindRoute( String DestinationName )
	{
		// Loop through the routes
		for( int RouteNum = 0; RouteNum < Routes.size(); RouteNum++ )
		{
			// If the current route destination name matches the search name, return the route
			if( Routes.get( RouteNum ).Destination.Name.equals( DestinationName ) )
			{
				return Routes.get( RouteNum );
			}
		}
		
		// If we get here, the route was not found
		return null;
	}
}
