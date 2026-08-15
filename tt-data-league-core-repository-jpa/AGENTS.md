# JPA repository module instructions

## Scope

This module is the persistence adapter for the domain module. Its current documentation is `docs/rfetm-datamodel.md`; implementation sources may be added under `src/main/java` using the existing package root `org.cttelsamicsterrassa.data.core`.

## Persistence rules

- Use Jakarta Persistence (`jakarta.persistence`) with Spring Boot 3.x, never legacy `javax.persistence`.
- Treat `docs/rfetm-datamodel.md` as the source of truth for table names, columns, nullability, relationships, uniqueness, enum storage, and aggregate behavior.
- Use `@Entity`, explicit `@Table`, `@Column`, `@JoinColumn`, and `@Enumerated(EnumType.STRING)` as specified by the model.
- Keep `@ManyToOne` and `@OneToMany` associations lazy. Initialize collection-valued associations with `new ArrayList<>()`.
- Preserve the documented cascade/orphan-removal behavior and unique constraints, especially match/game ordering and lineup uniqueness.
- Avoid exposing lazy entities through uncontrolled `toString`, `equals`, or `hashCode` implementations.
- Use Lombok only where it matches the documented entity convention (`@Getter`, `@Setter`, `@NoArgsConstructor`, and `@AllArgsConstructor`); do not generate broad convenience APIs that bypass invariants.
- Be careful with `MATCH`, which may be reserved by databases; follow the documented safe table-name strategy when implementing it.

## Dependency boundary

Depend on `tt-data-league-core-domain` for domain concepts. Do not modify domain classes to satisfy ORM mapping needs. If an entity model differs from the immutable domain model, use an explicit mapper or adapter and keep conversion behavior visible.

## Documentation and testing

Update `docs/rfetm-datamodel.md` when persistence behavior or schema constraints change. Add repository tests under `src/test/java`, using the existing Spring Boot test and H2 dependencies for mapping and persistence behavior. Run:

```text
mvn -pl tt-data-league-core-repository-jpa -am test
```

For schema-related changes, test both successful persistence and constraint/invariant failures; do not mask database errors with broad exception handling.
