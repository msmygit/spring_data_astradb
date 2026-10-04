package com.madhavan.demo.spring_data_astradb.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ASTRA_DB_APPLICATION_TOKEN", matches = ".+")
class BookRepositoryTests {

	@Autowired
	private BookRepository repository;

	@Test
	void crudRoundTrip() {
		UUID id = UUID.randomUUID();
		repository.save(new Book(id, "Dune", "Frank Herbert", 1965));

		assertThat(repository.findById(id)).hasValueSatisfying(book -> {
			assertThat(book.getTitle()).isEqualTo("Dune");
			assertThat(book.getAuthor()).isEqualTo("Frank Herbert");
		});

		repository.save(new Book(id, "Dune Messiah", "Frank Herbert", 1969));
		assertThat(repository.findById(id)).hasValueSatisfying(
				book -> assertThat(book.getTitle()).isEqualTo("Dune Messiah"));

		repository.deleteById(id);
		assertThat(repository.findById(id)).isEmpty();
	}

}
