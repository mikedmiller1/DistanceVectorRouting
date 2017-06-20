import java.io.*;

public class Route implements Serializable
{
	/*
	 * Properties
	 */
	private static final long serialVersionUID = 1L;
	public Node Source;
	public Node Destination;
	public Node NextRouter;
	public Double Cost;
	
	
	
	/*
	 * Default constructor
	 */
	public Route()
	{
		this( new Node(), new Node(), new Node(), 0.0 );
	}
	
	
	
	/*
	 * Constructor
	 */
	public Route( Node Source, Node Destination, Node NextRouter, Double Cost )
	{
		this.Source = Source;
		this.Destination = Destination;
		this.NextRouter = NextRouter;
		this.Cost = Cost;
	}
	
	
	
	/*
	 * Copy constructor
	 */
	public Route( Route original )
	{
		this.Source = original.Source;
		this.Destination = original.Destination;
		this.NextRouter = original.NextRouter;
		this.Cost = original.Cost;
	}
	
	
	
	/*
	 * Returns a copy of the object.
	 */
	public Route DeepClone()
	{
		return new Route( this );
	}
	
	
	
	@Override
	public String toString()
	{
		return "Shortest path " + Source + "-" + Destination + ": the next hop is " + NextRouter + " and the cost is " + Cost;
	}
}
