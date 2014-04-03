package org.apache.cassandra.db.index.stratio.schema;

import java.nio.ByteBuffer;

import org.apache.cassandra.db.marshal.AbstractType;
import org.apache.cassandra.db.marshal.ListType;
import org.apache.cassandra.db.marshal.MapType;
import org.apache.cassandra.db.marshal.SetType;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.codehaus.jackson.annotate.JsonSubTypes;
import org.codehaus.jackson.annotate.JsonTypeInfo;

/**
 * Class for mapping between Cassandra's columns and Lucene's documents.
 * 
 * @author adelapena
 * 
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({ @JsonSubTypes.Type(value = CellMapperBlob.class, name = "bytes"),
               @JsonSubTypes.Type(value = CellMapperBoolean.class, name = "boolean"),
               @JsonSubTypes.Type(value = CellMapperDate.class, name = "date"),
               @JsonSubTypes.Type(value = CellMapperDouble.class, name = "double"),
               @JsonSubTypes.Type(value = CellMapperFloat.class, name = "float"),
               @JsonSubTypes.Type(value = CellMapperInetAddress.class, name = "inet"),
               @JsonSubTypes.Type(value = CellMapperInteger.class, name = "integer"),
               @JsonSubTypes.Type(value = CellMapperLong.class, name = "long"),
               @JsonSubTypes.Type(value = CellMapperString.class, name = "string"),
               @JsonSubTypes.Type(value = CellMapperText.class, name = "text"),
               @JsonSubTypes.Type(value = CellMapperUUID.class, name = "uuid"),
               @JsonSubTypes.Type(value = CellMapperBigDecimal.class, name = "bigdec"),
               @JsonSubTypes.Type(value = CellMapperBigInteger.class, name = "bigint"), })
public abstract class CellMapper<BASE> {

	protected static final Analyzer EMPTY_ANALYZER = new KeywordAnalyzer();

	protected static final Store STORE = Store.NO;

	protected final AbstractType<?>[] supportedTypes;

	protected CellMapper(AbstractType<?>[] supportedTypes) {
		this.supportedTypes = supportedTypes;
	}

	public static Cell cell(String name, ByteBuffer value, AbstractType<?> type) {
		return new Cell(name, value, type);
	}

	public static Cell cell(String name, String nameSufix, ByteBuffer value, AbstractType<?> type) {
		return new Cell(name, nameSufix, value, type);
	}

	public abstract Analyzer analyzer();

	/**
	 * Returns the Lucene's {@link org.apache.lucene.document.Field} resulting from the mapping of
	 * {@code value}, using {@code name} as field's name.
	 * 
	 * @param name
	 *            The name of the Lucene's field.
	 * @param value
	 *            The value of the Lucene's field.
	 * @return The Lucene's {@link org.apache.lucene.document.Field} resulting from the mapping of
	 *         {@code value}, using {@code name} as field's name.
	 */
	public abstract Field field(String name, Object value);

	/**
	 * Returns the Lucene's type for this mapper.
	 * 
	 * @return The Lucene's type for this mapper.
	 */
	public abstract Class<BASE> baseClass();

	/**
	 * Returns the cell value resulting from the mapping of the specified object.
	 * 
	 * @param value
	 *            The object to be mapped.
	 * @return The cell value resulting from the mapping of the specified object.
	 */
	public abstract BASE indexValue(Object value);

	public abstract BASE queryValue(Object value);

	public boolean supports(final AbstractType<?> type) {

		AbstractType<?> checkedType = type;
		if (type.isCollection()) {
			if (type instanceof MapType<?, ?>) {
				checkedType = ((MapType<?, ?>) type).values;
			} else if (type instanceof ListType<?>) {
				checkedType = ((ListType<?>) type).elements;
			} else if (type instanceof SetType) {
				checkedType = ((SetType<?>) type).elements;
			}
		}

		for (AbstractType<?> n : supportedTypes) {
			if (checkedType.getClass() == n.getClass()) {
				return true;
			}
		}
		return false;
	}

}