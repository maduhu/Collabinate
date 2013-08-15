package com.collabinate.server;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.restlet.Component;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Method;

import com.tinkerpop.blueprints.impls.tg.TinkerGraph;

/**
 * Tests for the Stream Resource
 * 
 * @author mafuba
 * 
 */
public class StreamResourceTest
{
	GraphServer server;
	Component component;
	
	@Before
	public void Setup()
	{
		server = new GraphServer(new TinkerGraph());
		component = new CollabinateComponent(server, server);
	}
	
	@After
	public void Teardown() throws Exception
	{
		if (component.isStarted())
		{
			component.stop();
		}
	}
	
	
	@Test
	public void empty_stream_should_return_empty_response()
	{
		Request request = new Request(Method.GET, 
				"riap://application/1/entity/stream");
		Response response = component.handle(request);
		
		assertEquals(0, response.getEntity().getSize());
	}
}
