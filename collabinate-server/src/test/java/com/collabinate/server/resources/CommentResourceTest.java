package com.collabinate.server.resources;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.restlet.Request;
import org.restlet.data.Conditions;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Status;
import org.restlet.data.Tag;

import com.google.gson.JsonParser;

/**
 * Tests for the Comment Resource
 * 
 * @author mafuba
 * 
 */
public class CommentResourceTest extends GraphResourceTest
{
	@Test
	public void get_nonexistent_comment_should_return_404()
	{
		assertEquals(Status.CLIENT_ERROR_NOT_FOUND, get().getStatus());
	}
	
	@Test
	public void putting_comment_should_return_200()
	{
		assertEquals(Status.SUCCESS_OK, put().getStatus());
	}

	@Test
	public void get_should_return_json_content_type()
	{
		put("test", MediaType.TEXT_PLAIN);
		
		assertEquals(MediaType.APPLICATION_JSON,
				get().getEntity().getMediaType());
	}
	
	@Test
	public void get_response_should_contain_etag_header()
	{
		put("test", MediaType.TEXT_PLAIN);
		
		assertTrue(null != get().getEntity().getTag());
	}
	
	@Test
	public void etag_should_change_when_comment_changes()
	{
		put("test", MediaType.TEXT_PLAIN);
		Tag tag1 = get().getEntity().getTag();
		put("test2", MediaType.TEXT_PLAIN);
		Tag tag2 = get().getEntity().getTag();
		
		assertNotEquals(tag1, tag2);
	}
	
	@Test
	public void matching_etag_should_return_304_for_get()
	{
		put("test", MediaType.TEXT_PLAIN);
		Tag etag = get().getEntity().getTag();
		Request request = getRequest(Method.GET, null);
		Conditions conditions = new Conditions();
		conditions.getNoneMatch().add(etag);
		request.setConditions(conditions);
		
		assertEquals(Status.REDIRECTION_NOT_MODIFIED,
				getResponse(request).getStatus());
	}
	
	@Test
	public void non_matching_etag_should_return_200_for_get()
	{
		put("test", MediaType.TEXT_PLAIN);
		Request request = getRequest(Method.GET, null);
		Conditions conditions = new Conditions();
		conditions.getNoneMatch().add(new Tag("abc"));
		request.setConditions(conditions);
		
		assertEquals(Status.SUCCESS_OK, getResponse(request).getStatus());
	}
	
	@Test
	public void non_matching_etag_should_return_412_for_put()
	{
		put("test", MediaType.TEXT_PLAIN);
		Request request = getRequest(Method.PUT, null);
		Conditions conditions = new Conditions();
		conditions.getMatch().add(new Tag("abc"));
		request.setConditions(conditions);
		
		assertEquals(Status.CLIENT_ERROR_PRECONDITION_FAILED,
				getResponse(request).getStatus());
	}
	
	@Test
	public void matching_etag_should_return_200_for_put()
	{
		put("test", MediaType.TEXT_PLAIN);
		Tag etag = get().getEntity().getTag();
		Request request = getRequest(Method.PUT, null);
		Conditions conditions = new Conditions();
		conditions.getMatch().add(etag);
		request.setConditions(conditions);
		
		assertEquals(Status.SUCCESS_OK, getResponse(request).getStatus());
	}

	@Test
	public void get_existing_comment_should_return_comment()
	{
		put("test", MediaType.TEXT_PLAIN);
		assertThat(get().getEntityAsText(), containsString("test"));		
	}
	
	@Test
	public void comment_should_be_json_object()
	{
		put("test", MediaType.TEXT_PLAIN);
		// parser will throw if result is not json
		new JsonParser().parse(get().getEntityAsText());
	}
	
	@Test
	public void comment_with_different_id_than_url_should_return_422()
	{	
		assertEquals(Status.CLIENT_ERROR_UNPROCESSABLE_ENTITY,
			put("{\"id\":\"test\"}", MediaType.APPLICATION_JSON).getStatus());
	}
	
	@Test
	public void delete_nonexistent_comment_should_return_200()
	{
		assertEquals(Status.SUCCESS_OK, delete().getStatus());
	}
	
	@Test
	public void get_deleted_comment_should_return_404()
	{
		put();
		delete();
		assertEquals(Status.CLIENT_ERROR_NOT_FOUND, get().getStatus());
	}

	/**
	 * Prepares for comment work by creating an activity.
	 */
	@Before
	public void addActivity()
	{
		component.handle(new Request(Method.PUT,
			"riap://application/1/tenant/entities/entity/stream/activity"));
	}
	
	@Override
	protected String getResourcePath()
	{
		return "/1/tenant/entities/entity/stream/activity/" +
				"comments/comment?userId=user";
	}

}
