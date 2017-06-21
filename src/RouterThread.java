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
		
		// Add a route to this router with a cost 0
		Routes.add( new Route( ThisRouter, ThisRouter, ThisRouter, 0.0 ) );
		
		// Read the link file
		UpdateLinks();
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
				ArrayList<Route> ReceivedRoutes = (ArrayList<Route>)FileUtilities.fromString( new String( packet.getData(), 0, packet.getLength() ) );
				System.out.println(ReceivedRoutes.toString());
				
				// Update the Routes list
				UpdateRoutes( ReceivedRoutes );
			}

			catch( IOException | ClassNotFoundException e)
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
		UpdateLinks();
		
		
		// Loop through the links
		System.out.println( "Sending to " + Links.size() + " nodes" );
		for( int RouteNum = 0; RouteNum < Links.size(); RouteNum++ )
		{
			// Get the current link
			Link CurrentLink = Links.get( RouteNum );
			
			// Create a copy of the routes
			ArrayList<Route> RoutesToSend = new ArrayList<Route>( Routes );
			
			// Perform poisoned reverse on routes to adjacent links
			RoutesToSend = PoisonedReverse( RoutesToSend, CurrentLink);
			
			// Serialize the list of routes
			String RoutesString = FileUtilities.toString( RoutesToSend );
			
			
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
	 * Updates the Routes list with information from the new routes.
	 */
	protected void UpdateRoutes( ArrayList<Route> NewRoutes )
	{
		// TODO: Perform distance vector algorithm to update routing list
		double oldCost, newCost;
		for(int newRoute = 0; newRoute < NewRoutes.size(); newRoute++){
			
				Route oldRoute = FindRoute(NewRoutes.get(newRoute).Destination.Name);
				Route routeToSource = FindRoute(NewRoutes.get(newRoute).Source.Name);
				newCost = routeToSource.Cost + NewRoutes.get(newRoute).Cost;
				
				if(oldRoute == null){
					Route routeAdd = new Route();
					
					routeAdd.Cost = newCost;
					routeAdd.Source = ThisRouter;
					routeAdd.Destination = NewRoutes.get(newRoute).Destination;
					routeAdd.NextRouter = routeToSource.Destination;
					Routes.add(routeAdd);
					System.out.println("New Route added");
					System.out.println(routeAdd);
				}else{
					
					oldCost = oldRoute.Cost;
					if(newCost < oldCost){
						
						
						oldRoute.Cost = newCost;
						oldRoute.NextRouter = NewRoutes.get(newRoute).Destination;
						
					}
					
			
				}
				
				
			}
			
			
		}
	

	
	
	/*
	 * Reads the link file and updates the list of links and routes.
	 */
	protected void UpdateLinks() throws IOException
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
	
	
	
	ArrayList<Route> PoisonedReverse( ArrayList<Route> RouteList, Link CurrentLink )
	{
		// Loop through the routes
		for( int RouteNum = 0; RouteNum < RouteList.size(); RouteNum++ )
		{			
			// If the current route next hop is a link node
			if( RouteList.get( RouteNum ).NextRouter.Name.equals( CurrentLink.Node.Name ) )
			{
				// Set the route cost to 16 (poisoned reverse)
				RouteList.get( RouteNum ).Cost = 16.0;
			}					
		}
		
		// Return the modified routes
		return RouteList;
	}
	
	
	
	/*
	 * Returns the route stored in Routes to the specified destination router name.
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
	
	
	
	/*
	 * Returns the link stored in Links matching the specified name.
	 * If no link is found, returns null.
	 */
	Link FindLink( String LinkName )
	{
		// Loop through the links
		for( int LinkNum = 0; LinkNum < Links.size(); LinkNum++ )
		{
			// If the current link name matches the search name, return the link
			if( Links.get( LinkNum ).Node.Name.equals( LinkName ) )
			{
				return Links.get( LinkNum );
			}
		}
		
		// If we get here, the link was not found
		return null;
	}
}
