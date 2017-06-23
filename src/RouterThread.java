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
	synchronized protected void Broadcast() throws IOException
	{
		// Increment the broadcast number
		BroadcastNumber++;
		System.out.println("Broadcast number " + BroadcastNumber);
		
		System.out.println(new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date()));
		
		// Print the current routing table
		PrintRoutingTable();
		
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
	synchronized protected void UpdateRoutes( ArrayList<Route> NewRoutes ) throws IOException
	{
		System.out.println( "Updating routes" );
		System.out.println(new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date()));
		double oldCost, newCost;
		boolean BetterRouteFound = false;
		
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
				routeAdd.NextRouter = routeToSource.NextRouter; 
				Routes.add( routeAdd );
				BetterRouteFound = true;
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
					oldRoute.NextRouter = CurrentNewRoute.Source;
					BetterRouteFound = true;
					System.out.println( "Updated existing route: " + oldRoute );
				}
				else if( oldRoute.NextRouter.Name.equals(CurrentNewRoute.Source.Name) ){
					
						double difference = CurrentNewRoute.Cost + routeToSource.Cost - oldRoute.Cost; 
						oldRoute.Cost = oldRoute.Cost + difference;
						System.out.println("Changed cost by "+difference);
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
		
		// Print the current routing table
		PrintRoutingTable();
		
		
		// Check if a better route was found during the updates
		if( BetterRouteFound )
		{
			// If so, immediately send a broadcast
			System.out.println( "Better route found, sending broadcast" );
			Broadcast();
			
			// Reset the timer
			BroadcastTimer.cancel();
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
		}
	}
	
	
	
	/*
	 * Reads the link file and updates the list of links and routes.
	 */
	synchronized protected void UpdateLinks() throws IOException
	{
		System.out.print("Updating links from file... ");
		boolean LinkChanged = false;
		PrintRoutingTable();
		// Clear the links list
		//Links.clear();

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
			
			Link OldLink = FindLink(NewLink.Node.Name);
			if(OldLink == null){
				Links.add( NewLink );
				Route r = new Route(ThisRouter, NewLink.Node, NewLink.Node, NewLink.Cost);
				Routes.add(r);
				System.out.println("New Link and Route added "+ r);
				LinkChanged = true;
			}
			else{
				double difference = NewLink.Cost - OldLink.Cost;
				for( int RouteNum = 0; RouteNum < Routes.size(); RouteNum ++ ){
					if( Routes.get(RouteNum).NextRouter.Name.equals(NewLink.Node.Name)){
						System.out.println("Cost of Route: "+Routes.get(RouteNum).Cost +" diff: "+difference);
						Routes.get(RouteNum).Cost = Routes.get(RouteNum).Cost + difference;
						System.out.println("Updated route cost "+Routes.get(RouteNum));
					}
					if( Routes.get(RouteNum).Destination.Name.equals(NewLink.Node.Name)){
						if(Routes.get(RouteNum).Cost > NewLink.Cost){
							Routes.get(RouteNum).NextRouter = NewLink.Node;
							Routes.get(RouteNum).Cost = NewLink.Cost;
						}
							
					}
				}
				
				if(difference != 0){
					System.out.println("Difference: "+difference+" New Link Cost: "+NewLink.Cost);
					OldLink.Cost = NewLink.Cost;
					
					LinkChanged = true;
				}
			}
			
			
			/*// Check if there is already a route to the link
			Route CurrentRoute = FindRoute( NewLink.Node.Name );
			
			// If there is an existing route and the route cost is more than the link cost
			if( CurrentRoute != null )
			{
				if( NewLink.Cost < CurrentRoute.Cost )
				{
					// Update the route
					CurrentRoute.NextRouter = NewLink.Node;
					CurrentRoute.Cost = NewLink.Cost;
				}
			}
			
			// Otherwise, create a new route
			else
			{
				Routes.add( new Route( ThisRouter, NewLink.Node, NewLink.Node, NewLink.Cost ) );
			}*/
		}
		

		System.out.println("Done");
		System.out.println("Found " + NumberOfLinks + " links");
		PrintRoutingTable();
		

		if( LinkChanged ){
			Broadcast();
		}
	}
	
	
	
	/*
	 * Adjusts the cost of routes to perform poisoned reverse to the specified link.
	 */
	protected ArrayList<Route> PoisonedReverse( ArrayList<Route> RouteList, Link CurrentLink )
	{
		// Loop through the routes
		for( int RouteNum = 0; RouteNum < RouteList.size(); RouteNum++ )
		{
			// If the current route next hop is the link node we are sending the routes to
			if( RouteList.get( RouteNum ).NextRouter.Name.equals( CurrentLink.Node.Name ) )
			{
				// Set the route cost to 16 (poisoned reverse)
				// Only if the node follows another path to reach the destination and does not directly reach the destination
				if( ! (RouteList.get( RouteNum ).NextRouter.Name.equals( RouteList.get( RouteNum ).Destination.Name ) ) )
				{
					// The route cost to reach directly to the destination is set to 16.0
					RouteList.add(new Route(ThisRouter, RouteList.get( RouteNum ).Destination, RouteList.get( RouteNum ).Destination, 16.0));
				}
			}					
		}
		
		// Return the modified routes
		return RouteList;
	}
	
	
	
	/*
	 * Returns the route stored in Routes to the specified destination router name.
	 * If no route is found, returns null.
	 */
	synchronized protected Route FindRoute( String DestinationName )
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
	synchronized protected Link FindLink( String LinkName )
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
	
	
	
	/*
	 * Prints the current routing list
	 */
	synchronized public void PrintRoutingTable()
	{
		System.out.println( "" );
		System.out.println( "***** Routing Table *****" );

		// Loop through the routes
		for( int RouteNum = 0; RouteNum < Routes.size(); RouteNum++ )
		{
			// Print the current route
			System.out.println( Routes.get( RouteNum ) );
		}
		System.out.println( "" );	
	}
}
