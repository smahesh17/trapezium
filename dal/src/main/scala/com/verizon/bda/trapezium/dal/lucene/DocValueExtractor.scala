package com.verizon.bda.trapezium.dal.lucene

import com.verizon.bda.trapezium.dal.exceptions.LuceneDAOException
import org.apache.spark.sql.Row
import org.apache.spark.sql.types._
import org.slf4j.LoggerFactory

/**
 * @author debasish83 on 12/22/16.
 *         Supports primitives for extracting doc value fields
 */
class DocValueExtractor(leafReaders: Seq[LuceneReader],
                        converter: OLAPConverter) extends Serializable {

  private val logger = LoggerFactory.getLogger(this.getClass)
  val schema = converter.schema
  val dimensions = converter.dimensions
  val storedDimensions = converter.storedDimensions
  val measures = converter.measures
  val ser = converter.ser

  private val dvMap: Map[String, DocValueAccessor] = if (leafReaders.length > 0) {
    val fields = schema.filter(field => {
      storedDimensions.contains(field.name) || measures.contains(field.name)
    })

    fields.map { case (field) =>
      val fieldName = field.name
      val fieldMultiValued = (field.dataType.isInstanceOf[ArrayType])
      // Dimensions have gone through DictionaryEncoding and uses sorted-setnumeric storage
      val accessor = if (storedDimensions.contains(fieldName)) {
        DocValueAccessor(leafReaders, fieldName, IntegerType, true, ser)
      } else {
        DocValueAccessor(leafReaders, fieldName, field.dataType, fieldMultiValued, ser)
      }
      fieldName -> accessor
    }.toMap
  } else {
    Map.empty[String, DocValueAccessor]
  }

  private def extractStored(docID: Int, column: String): Any = {
    val offset = dvMap(column).getOffset(docID)
    if (offset <= 0) return null
    //multi-value dimension/measure
    if (offset > 1) Seq((0 until offset).map(dvMap(column).extract(docID, _)): _*)
    else dvMap(column).extract(docID, 0)
  }

  // only storedDimensions and measures can be extracted
  def extract(columns: Seq[String], docID: Int): Row = {
    if (dvMap.size > 0) {
      val sqlFields = columns.map((column) => {
        if (storedDimensions.contains(column) || measures.contains(column))
          extractStored(docID, column)
        else throw new LuceneDAOException(s"unsupported ${column} in doc value extraction")
      })
      Row.fromSeq(sqlFields)
    } else {
      Row.empty
    }
  }

  def getOffset(column: String, docID: Int): Int = {
    dvMap(column).getOffset(docID)
  }

  def extract(column: String, docID: Int, offset: Int): Any = {
    dvMap(column).extract(docID, offset)
  }
}

object DocValueExtractor {
  def apply(leafReaders: Seq[LuceneReader],
            converter: OLAPConverter): DocValueExtractor = {
    new DocValueExtractor(leafReaders, converter)
  }
}
