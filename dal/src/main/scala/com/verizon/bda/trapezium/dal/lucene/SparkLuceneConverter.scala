package com.verizon.bda.trapezium.dal.lucene

/**
 * @author pramod.lakshminarasimha Spark Lucene Converterf or stored fields
 *         15 Dec 2016 debasish83 Converter for OLAP queries supporting indexed, DocValues and stored fields
 */

import com.verizon.bda.trapezium.dal.exceptions.LuceneDAOException
import org.apache.lucene.util.BytesRef
import org.apache.spark.serializer.SerializerInstance
import org.apache.spark.sql.Row
import org.apache.spark.sql.types._
import org.apache.lucene.document._
import java.sql.Timestamp
import org.apache.lucene.index.IndexableField
import org.slf4j.LoggerFactory
import org.apache.spark.ml.linalg.SQLDataTypes.VectorType
import org.apache.spark.ml.linalg.Vector
import org.apache.spark.util.DalUtils

trait SparkLuceneConverter extends SparkSQLProjections with Serializable  {

  private val log = LoggerFactory.getLogger(this.getClass)

  def rowToDoc(r: Row): Document

  def docToRow(d: Document): Row

  def schema: StructType

  val ser: SerializerInstance

  // Solr / Elastisearch uses schema.xml to achieve this while trapezium supports SparkSQL types
  def toIndexedField(name: String,
                     dataType: DataType,
                     value: Any,
                     store: Field.Store): Field = {
    dataType match {
      case StringType => new TextField(name, value.asInstanceOf[String], store)
      // On integer, long, float and double we do want to push range queries and indexing distinct
      // value makes no sense
      case IntegerType => new LegacyIntField(name, value.asInstanceOf[Int], store)
      case LongType => new LegacyLongField(name, value.asInstanceOf[Long], store)
      case FloatType => new LegacyFloatField(name, value.asInstanceOf[Float], store)
      case DoubleType => new LegacyDoubleField(name, value.asInstanceOf[Double], store)
      case _ => throw new LuceneDAOException(s"unsupported sparksql ${dataType} for indexed field")
    }
  }

  def toDocValueField(name: String,
                      dataType: DataType,
                      multivalued: Boolean,
                      value: Any): Field = {
    if (multivalued) {
      assert(dataType == IntegerType, "multi-valued dimensions must be integer")
      return new SortedNumericDocValuesField(name, value.asInstanceOf[Int])
    }

    dataType match {
      case IntegerType =>
        new NumericDocValuesField(name, value.asInstanceOf[Int])
      case LongType =>
        new NumericDocValuesField(name, value.asInstanceOf[Long])
      case FloatType =>
        new FloatDocValuesField(name, value.asInstanceOf[Float])
      case DoubleType =>
        new DoubleDocValuesField(name, value.asInstanceOf[Double])
      case TimestampType =>
        new NumericDocValuesField(name, value.asInstanceOf[Timestamp].getTime)
      case StringType =>
        val bytes = value.asInstanceOf[String].getBytes("UTF-8")
        new SortedDocValuesField(name, new BytesRef(bytes))
      // For sketches (HLL/MinHash/BitMap) we use BinaryType to ser/deser in DataFrame
      case BinaryType =>
        val bytes = value.asInstanceOf[Array[Byte]]
        new BinaryDocValuesField(name, new BytesRef(bytes))
      case VectorType =>
        val bytes = VectorProjection(DalUtils.serializeVector(value.asInstanceOf[Vector])).getBytes
        new BinaryDocValuesField(name, new BytesRef(bytes))
      case _ => log.info(s"serializing ${dataType.typeName} as binary doc value field")
        val bytes = ser.serialize(value).array()
        new BinaryDocValuesField(name, new BytesRef(bytes))
    }
  }

  def fromStoredField(field: IndexableField,
                      dataType: DataType) : Any = {
    dataType match {
      case StringType => field.stringValue()
      case IntegerType => field.numericValue().intValue()
      case LongType => field.numericValue().longValue()
      case FloatType => field.numericValue().floatValue()
      case DoubleType => field.numericValue().doubleValue()
      case _ =>
        throw new LuceneDAOException(s"unsupported sparksql ${dataType} from indexed field")
    }
  }
}
