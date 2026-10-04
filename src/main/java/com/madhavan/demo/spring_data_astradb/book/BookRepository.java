package com.madhavan.demo.spring_data_astradb.book;

import java.util.UUID;

import org.springframework.data.cassandra.repository.CassandraRepository;

public interface BookRepository extends CassandraRepository<Book, UUID> {

}
