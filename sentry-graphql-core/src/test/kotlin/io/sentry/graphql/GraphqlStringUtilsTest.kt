package io.sentry.graphql

import graphql.execution.MergedField
import graphql.language.Field
import graphql.scalar.GraphqlStringCoercing
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLScalarType
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class GraphqlStringUtilsTest {
  @Test
  fun `field to String`() {
    val mergedField =
      MergedField.newMergedField().addField(Field.newField("myFieldName").build()).build()
    val string = GraphqlStringUtils.fieldToString(mergedField)
    assertEquals("myFieldName", string)
  }

  @Test
  fun `null field to String`() {
    assertNull(GraphqlStringUtils.fieldToString(null))
  }

  @Test
  fun `type to String`() {
    val scalarType =
      GraphQLScalarType.newScalar().name("MyResponseType").coercing(GraphqlStringCoercing()).build()
    val string = GraphqlStringUtils.typeToString(scalarType)
    assertEquals("MyResponseType", string)
  }

  @Test
  fun `null type to String`() {
    assertNull(GraphqlStringUtils.typeToString(null))
  }

  @Test
  fun `objectType to String`() {
    val scalarType =
      GraphQLScalarType.newScalar().name("MyResponseType").coercing(GraphqlStringCoercing()).build()
    val field =
      GraphQLFieldDefinition.newFieldDefinition().name("myQueryFieldName").type(scalarType).build()
    val objectType = GraphQLObjectType.newObject().name("QUERY").field(field).build()
    val string = GraphqlStringUtils.objectTypeToString(objectType)
    assertEquals("QUERY", string)
  }

  @Test
  fun `null objectType to String`() {
    assertNull(GraphqlStringUtils.objectTypeToString(null))
  }
}
