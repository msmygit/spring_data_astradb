package com.madhavan.demo.spring_data_astradb.book;

import java.util.UUID;

import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

@Table("books")
public class Book {

	@PrimaryKey
	private UUID id;

	private String title;

	private String author;

	private Integer publishedYear;

	public Book() {
	}

	public Book(UUID id, String title, String author, Integer publishedYear) {
		this.id = id;
		this.title = title;
		this.author = author;
		this.publishedYear = publishedYear;
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getAuthor() {
		return author;
	}

	public void setAuthor(String author) {
		this.author = author;
	}

	public Integer getPublishedYear() {
		return publishedYear;
	}

	public void setPublishedYear(Integer publishedYear) {
		this.publishedYear = publishedYear;
	}

}
