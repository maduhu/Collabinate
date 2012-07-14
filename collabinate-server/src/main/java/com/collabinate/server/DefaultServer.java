package com.collabinate.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.joda.time.DateTime;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.KeyIndexableGraph;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.util.wrappers.id.IdGraph;

public class DefaultServer implements CollabinateReader, CollabinateWriter
{
	private KeyIndexableGraph graph;
	
	public DefaultServer(final KeyIndexableGraph graph)
	{
		if (null == graph)
		{
			throw new IllegalArgumentException("graph must not be null");
		}
		if (graph.getFeatures().ignoresSuppliedIds)
			this.graph = new IdGraph<KeyIndexableGraph>(graph);
		else
			this.graph = graph;
	}
	
	@Override
	public void addStreamItem(String entityId, StreamItemData streamItemData)
	{
		if (null == entityId)
		{
			throw new IllegalArgumentException("entityId must not be null");
		}
		
		if (null == streamItemData)
		{
			throw new IllegalArgumentException("streamItem must not be null");
		}
		
		Vertex entity = getOrCreateEntity(entityId);
		
		Vertex streamItem = createStreamItem(streamItemData);
		
		insertStreamItem(entity, streamItem);
		
		updateFeedPaths(entity);
	}

	private Vertex getOrCreateEntity(String entityId)
	{
		Vertex entity = graph.getVertex(entityId);		
		if (null == entity)
		{
			entity = graph.addVertex(entityId);
		}		
		return entity;
	}
	
	private Vertex createStreamItem(StreamItemData streamItemData)
	{
		Vertex streamItem = graph.addVertex(null);
		streamItem.setProperty("Time", streamItemData.getTime().toString());
		return streamItem;
	}
	
	private void insertStreamItem(Vertex entity, Vertex streamItem)
	{
		// get the edge to the first stream item, if any
		final Edge originalEdge = getStreamItemEdge(entity);
		
		// get the first stream item, if any, and remove the first edge
		Vertex previousStreamItem = null;		
		if (null != originalEdge)
		{
			previousStreamItem = originalEdge.getVertex(Direction.IN);
			graph.removeEdge(originalEdge);		
		}
		
		// connect the new stream item to the entity
		graph.addEdge(null, entity, streamItem, "StreamItem");
		
		// if there was a previous stream item,
		// connect the new stream item to it
		if (null != previousStreamItem)
		{
			graph.addEdge(null, streamItem, previousStreamItem, "StreamItem");
		}
	}
	
	private Edge getStreamItemEdge(Vertex entity)
	{
		Iterator<Edge> iterator = 
				entity.getEdges(Direction.OUT, "StreamItem").iterator();
		
		Edge edge = iterator.hasNext() ? iterator.next() : null;
		
		if (null != edge)
		{
			if (iterator.hasNext())
			{
				throw new IllegalStateException(
					"Multiple stream item edges for entity: " + 
					entity.getId());
			}
		}
		
		return edge;
	}
	
	private void updateFeedPaths(Vertex entity)
	{
		// get all the users that follow the entity
		Iterable<Vertex> users =
				entity.getVertices(Direction.IN, "Follows");
		
		// loop over each and move the entity to first
		for (Vertex user : users)
		{
			putFeedEntityFirst(user, entity);
		}
	}
	
	private void putFeedEntityFirst(Vertex user, Vertex entity)
	{
		String feedLabel = getFeedLabel(getIdString(user));
		
		// get the previous entity
		Vertex previous = getNextFeedEntity(
				feedLabel, entity, Direction.IN);
		
		// if the previous entity is the user, we're done
		if (user.getId().equals(previous.getId()))
		{
			return;
		}
		
		// get the next entity
		Vertex next = getNextFeedEntity(
				feedLabel, entity, Direction.OUT);
		
		// get the entity that was originally first
		Vertex originalFirst = getNextFeedEntity(
				feedLabel, user, Direction.OUT);
		
		// delete the old edges
		for (Edge edge : user.getEdges(Direction.OUT, feedLabel))
			graph.removeEdge(edge);
		for (Edge edge : previous.getEdges(Direction.OUT, feedLabel))
			graph.removeEdge(edge);
		for (Edge edge : entity.getEdges(Direction.OUT, feedLabel))
			graph.removeEdge(edge);
		
		// add the new edges
		graph.addEdge(null, user, entity, feedLabel);
		graph.addEdge(null, entity, originalFirst, feedLabel);
		if (null != next)
			graph.addEdge(null, previous, next, feedLabel);
	}
	
	private String getIdString(Vertex vertex)
	{
		return vertex.getId().toString();
	}

	@Override
	public List<StreamItemData> getStream(String entityId, long startIndex,
			int itemsToReturn)
	{
		Vertex entity = graph.getVertex(entityId);
		if (null == entity)
		{
			return new ArrayList<StreamItemData>();
		}
		
		int streamPosition = 0;
		int foundItemCount = 0;
		List<Vertex> streamVertices = new ArrayList<Vertex>();
		
		Vertex currentStreamItem = getNextStreamItem(entity);
		
		while (null != currentStreamItem && foundItemCount < itemsToReturn)
		{
			if (streamPosition >= startIndex)
			{
				streamVertices.add(currentStreamItem);
				foundItemCount++;
			}
			currentStreamItem = getNextStreamItem(currentStreamItem);
			streamPosition++;
		}
		
		return createStreamItems(streamVertices);
	}
	
	private Vertex getNextStreamItem(Vertex node)
	{
		Iterator<Vertex> vertices = 
				node.getVertices(Direction.OUT, "StreamItem").iterator();
		return vertices.hasNext() ? vertices.next() : null;
	}

	private List<StreamItemData> createStreamItems(Collection<Vertex> streamItems)
	{
		ArrayList<StreamItemData> itemData =
				new ArrayList<StreamItemData>();
		
		for (final Vertex vertex : streamItems)
		{
			if (null != vertex)
			{
				itemData.add(new StreamItemData() {
					
					@Override
					public DateTime getTime()
					{
						return DateTime.parse((String) vertex.getProperty("Time"));
					}
				});
			}
		}
		
		return itemData;
	}

	@Override
	public void followEntity(String userId, String entityId)
	{
		if (null == userId)
		{
			throw new IllegalArgumentException("userId must not be null");
		}
		
		Vertex user = getOrCreateEntity(userId);
		Vertex entity = getOrCreateEntity(entityId);
		
		graph.addEdge(null, user, entity, "Follows");
		
		addEntityToFeed(user, entity);
	}
	
	private void addEntityToFeed(Vertex user, Vertex entityToAdd)
	{
		String feedLabel = getFeedLabel(getIdString(user));
		DateTime entityDate = getTopStreamItemDate(entityToAdd);
		Vertex previousEntity = user;
		Vertex entity = getNextFeedEntity(
				feedLabel, previousEntity, Direction.OUT);
		DateTime currentEntityDate = getTopStreamItemDate(entity);
		while (null != entity && 
				entitySupercedes(currentEntityDate, entityDate))
		{
			previousEntity = entity;
			entity = getNextFeedEntity(
					feedLabel, previousEntity, Direction.OUT);
			currentEntityDate = getTopStreamItemDate(entity);
		}
		
		// add the edge from the previous entity
		graph.addEdge(null, previousEntity, entityToAdd, feedLabel);
		
		// if there's a current entity, remove the edge from
		// the previous to it, and add an edge from the added
		if (null != entity)
		{
			for (Edge edge: previousEntity.getEdges(Direction.OUT, feedLabel))
				graph.removeEdge(edge);
			graph.addEdge(null, entityToAdd, entity, feedLabel);
		}
	}
	
	private DateTime getTopStreamItemDate(Vertex entity)
	{
		if (null == entity)
			return null;
		
		List<StreamItemData> entityStream = 
				getStream(getIdString(entity), 0, 1);
		
		if (entityStream.size() < 1)
			return null;
		
		return entityStream.get(0).getTime();
	}
	
	private boolean entitySupercedes(
			DateTime currentEntityDate, DateTime entityDate)
	{
		if (null == entityDate)
			return false;
		if (null == currentEntityDate)
			return true;
		
		// negation to cover the equals case
		return !currentEntityDate.isAfter(entityDate);
	}
	
	@Override
	public List<StreamItemData> getFeed(String userId, long startIndex, int itemsToReturn)
	{
		Vertex user = getOrCreateEntity(userId);
		Vertex entity = getNextFeedEntity(
				getFeedLabel(getIdString(user)), user, Direction.OUT);
		List<Vertex> streamItems = new ArrayList<Vertex>();
		if (null != entity)
		{
			streamItems.add(getNextStreamItem(entity));
		}
		return createStreamItems(streamItems);
	}
	
	private Vertex getNextFeedEntity(String feedLabel,
			Vertex currentEntity, Direction direction)
	{
		Iterator<Vertex> vertices = 
				currentEntity
				.getVertices(direction, feedLabel)
				.iterator();
		return vertices.hasNext() ? vertices.next() : null;
		
	}
	
	private String getFeedLabel(String userId)
	{
		return "Feed+" + userId;
	}
}
