import java.io.*;
import java.net.*;
import java.util.*;

/**
 * @author Mike Miller, Neha Dalvi
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

	
	
	/*
	 * Called when the Start() method is run.
	 * (non-Javadoc)
	 * @see java.lang.Thread#run()
	 */
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
				System.out.println( "Route list received" );
				
				// Deserialize the packet data into a routes list
				@SuppressWarnings("unchecked")
				ArrayList<Route> ReceivedRoutes = (ArrayList<Route>)FileUtilities.fromString( new String( packet.getData(), 0, packet.getLength() ) );
				
				
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
			
			// Create a deep copy of the routes
			ArrayList<Route> RoutesToSend = new ArrayList<Route>();
			for( Route r : Routes )
			{
				RoutesToSend.add( new Route( r ) );
			}
			
			// Remove the first route (this is the current router's path to itself)
			RoutesToSend.remove( 0 );
			
			// Perform poisoned reverse on routes to the current link
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
		System.out.println( "Updating routes" );
		double oldCost, newCost;
		
		// Loop through the new routes
		for( int newRouteNum = 0; newRouteNum < NewRoutes.size(); newRouteNum++ )
		{
			// Get the current new route
			Route CurrentNewRoute = NewRoutes.get( newRouteNum );
			System.out.println( "Evaluating route: " + CurrentNewRoute );
			
			// Search for an existing route to the destination in the new route
			Route oldRoute = FindRoute( CurrentNewRoute.Destination.Name );
			
			// Get the existing route to the new route source router
			Route routeToSource = FindRoute( CurrentNewRoute.Source.Name );
			
			// Calculate the new route cost
			newCost = routeToSource.Cost + CurrentNewRoute.Cost;
			
			
			// If an existing route to the destination was not found
			if(oldRoute == null)
			{
				// Add the new route
				Route routeAdd = new Route();				
				routeAdd.Cost = newCost;
				routeAdd.Source = ThisRouter;
				routeAdd.Destination = CurrentNewRoute.Destination;
				routeAdd.NextRouter = routeToSource.Destination;
				Routes.add( routeAdd );
				System.out.println("Added new route: " + routeAdd );
			}
			
			
			// Otherwise, evaluate the new route compared to the existing route
			else
			{
				// Get the existing route cost
				oldCost = oldRoute.Cost;
				System.out.println( "Existing route cost is " + oldCost + ", new route cost is " + newCost );
				
				// If the new cost is less than the existing cost
				if( newCost < oldCost )
				{
					// Update the existing route to use the new route
					oldRoute.Cost = newCost;
					oldRoute.NextRouter = NewRoutes.get(newRouteNum).Destination;
					System.out.println( "Updated existing route: " + oldRoute );
				}
				
				// Otherwise, the existing route cost is better
				else
				{
					System.out.println( "Existing route cost is equal or better, no update performed" );
				}
			}
		}
		System.out.println( "Updating routes complete" );
		System.out.println();
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
	
	
	
	/*
	 * Adjusts the cost of routes to perform poisoned reverse to the specified link.
	 */
	ArrayList<Route> PoisonedReverse( ArrayList<Route> RouteList, Link CurrentLink )
	{
		// Loop through the routes
		for( int RouteNum = 0; RouteNum < RouteList.size(); RouteNum++ )
		{
			// If the current route next hop is the link node we are sending the routes to
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
