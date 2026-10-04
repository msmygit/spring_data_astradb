package com.madhavan.demo.spring_data_astradb.book;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
public class BookController {

	private final BookRepository repository;

	public BookController(BookRepository repository) {
		this.repository = repository;
	}

	@GetMapping
	public List<Book> findAll() {
		return repository.findAll();
	}

	@GetMapping("/{id}")
	public ResponseEntity<Book> findById(@PathVariable UUID id) {
		return ResponseEntity.of(repository.findById(id));
	}

	@PostMapping
	public ResponseEntity<Book> create(@RequestBody Book book) {
		book.setId(UUID.randomUUID());
		Book saved = repository.save(book);
		return ResponseEntity.created(URI.create("/api/books/" + saved.getId())).body(saved);
	}

	@PutMapping("/{id}")
	public ResponseEntity<Book> update(@PathVariable UUID id, @RequestBody Book book) {
		if (!repository.existsById(id)) {
			return ResponseEntity.notFound().build();
		}
		book.setId(id);
		return ResponseEntity.ok(repository.save(book));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		if (!repository.existsById(id)) {
			return ResponseEntity.notFound().build();
		}
		repository.deleteById(id);
		return ResponseEntity.noContent().build();
	}

}
