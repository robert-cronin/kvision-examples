package com.example

import com.github.andrewoma.kwery.core.Session
import com.github.andrewoma.kwery.core.ThreadLocalSession
import com.github.andrewoma.kwery.core.builder.query
import com.github.andrewoma.kwery.core.dialect.Dialect
import com.github.andrewoma.kwery.core.dialect.HsqlDialect
import com.github.andrewoma.kwery.core.dialect.PostgresDialect
import com.github.andrewoma.kwery.core.interceptor.LoggingInterceptor
import com.github.andrewoma.kwery.mapper.AbstractDao
import com.github.andrewoma.kwery.mapper.SimpleConverter
import com.github.andrewoma.kwery.mapper.Table
import com.github.andrewoma.kwery.mapper.TableConfiguration
import com.github.andrewoma.kwery.mapper.Value
import com.github.andrewoma.kwery.mapper.prefixed
import com.github.andrewoma.kwery.mapper.reifiedConverter
import com.github.andrewoma.kwery.mapper.standardConverters
import com.github.andrewoma.kwery.mapper.util.camelToLowerUnderscore
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.util.*
import javax.sql.DataSource

object DateConverter : SimpleConverter<Date>(
    { row, c -> Date(row.timestamp(c).time) },
    { Timestamp(it.time) }
)

val kvTableConfig = TableConfiguration(
    converters = standardConverters + reifiedConverter(DateConverter),
    namingConvention = camelToLowerUnderscore
)

object AddressTable : Table<Address, Int>("address", kvTableConfig) {
    val Id by col(Address::id, id = true)
    val FirstName by col(Address::firstName)
    val LastName by col(Address::lastName)
    val Email by col(Address::email)
    val Phone by col(Address::phone)
    val PostalAddress by col(Address::postalAddress)
    val Favourite by col(Address::favourite)
    val CreatedAt by col(Address::createdAt)
    val UserId by col(Address::userId)

    override fun idColumns(id: Int) = setOf(Id of id)

    override fun create(value: Value<Address>) = Address(
        value of Id,
        value of FirstName,
        value of LastName,
        value of Email,
        value of Phone,
        value of PostalAddress,
        value of Favourite,
        value of CreatedAt,
        value of UserId
    )
}

class AddressDao(session: Session) :
    AbstractDao<Address, Int>(session, AddressTable, { it.id ?: 0 }, "int", defaultId = 0) {

    val a = AddressTable.prefixed("a")

    fun findByCriteria(userId: String, search: String?, types: String, sort: Sort): List<Address> {
        val query = query {
            select("SELECT ${a.select} FROM address a")
            whereGroup {
                where("user_id = :user_id")
                parameter("user_id", userId)
                search?.let {
                    where(
                        """(lower(a.first_name) like :search
                            OR lower(a.last_name) like :search
                            OR lower(a.email) like :search
                            OR lower(a.phone) like :search
                            OR lower(a.postal_address) like :search)""".trimMargin()
                    )
                    parameter("search", "%${it.toLowerCase()}%")
                }
                if (types == "fav") {
                    where("a.favourite")
                }
            }
            when (sort) {
                Sort.FN -> orderBy("lower(a.first_name)")
                Sort.LN -> orderBy("lower(a.last_name)")
                Sort.E -> orderBy("lower(a.email)")
                Sort.F -> orderBy("a.favourite")
            }
        }
        return session.select(query.sql, query.parameters, mapper = a.mapper)
    }
}

@Service
class AddressDaoFactory(private val env: Environment, private val ds: DataSource) {

    fun getDbDialect(): Dialect {
        return when (env.getProperty("dbdialect", "hsql")) {
            "pgsql" -> PostgresDialect()
            else -> HsqlDialect()
        }
    }

    @Bean
    fun getAddresDao(): AddressDao {
        val session = ThreadLocalSession(ds, getDbDialect(), LoggingInterceptor())
        return AddressDao(session)
    }
}
