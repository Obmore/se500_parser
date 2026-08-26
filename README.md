# se500_parser

Az SPI gép XML eredményeit kell adatbázisba tenni.
Java 17 + PostgreSQL.

---

## Magyar

Minden teszt után bedobnak egy XML-t egy mappába. Kb. 50 fájl óránként, gépenként, 15 gép. A séma mindig ugyanaz, a mérési pontok száma nem. Gépenként más termékek jönnek (`Panel Name`).

Riportnál ezekre kell tudni szűrni:

- sorozatszám - `Panel/@Code`
- időpont - `Panel/@TestTime`
- eredmény - `Panel/@Status` (`P` pass, `F` fail)

Amit el kell tenni: melyik `Panel Name` / `Image Name` / `Location Name` / `Feature Name` alatt milyen Height, Area, Volume érték jött, a limitekkel együtt.

A minták a `samples` mappában vannak. CyberOptics XML, `Panel → Image → Location → Feature`. A Tls81777-es termékben durván 4 image, 328 location, 1184 feature, fájlonként ~1.5 MB. A `…59521099.xml` fail, a többi pass - ugyanaz a sorozatszám, más időpont.

### Miért így a séma

15 gép × 50 fájl/óra × 24 × 365 × 3 év az nagyjából 20 millió teszt. Ha a mintához hasonló a termék, feature szinten ez milliárdos tábla. Ezért nem rakok szöveget és limitet minden mérési sorba.

- `machine` / `product` - kevés sor, stringek itt vannak
- `feature_def` - a pad + a Height/Area/Volume limitek. Ugyanazon a terméken ezek nem változnak tesztől tesztig, felesleges 1184-szer újra leírni
- `inspection` - egy fájl, egy teszt. Itt van a sorozatszám, az idő, a P/F. Index: `(serial_code, test_time, status)`
- `measurement` - csak három szám (`real`) + két id. Saját surrogate key nincs, `(inspection_id, feature_def_id)` a kulcs

A limiteket az első találatkor írom be. Ha egyszer valaki megváltoztatja a receptet, ez nem követi, cserébe a tábla kicsi marad. 3 év után a régi `inspection` sorokat lehet törölni, a mérések CASCADE-del mennek velük.

Nem Spring, nem Hibernate. JDBC + StAX. A fájl streamelve olvasható, a méréseket batch-elve írom. Ugyanazt a fájlnevet másodszor kihagyja. Ha egy XML félbemarad, rollback, a következő fájllal megy tovább.

PHP / MySQL is belefért volna a kiírásba. MySQL-en a laterális lookup ugyanúgy megoldható, de a Postgres `ON CONFLICT` és a `RETURNING` kényelmesebb ennél a feltöltésnél.

### Futtatás

Postgres (Docker):

```
docker compose up -d
```

Utána JDK 17 + Maven:

```
mvn compile exec:java -Dexec.args=samples
```

A kapcsolat a `src/main/resources/jdbc.properties`-ben van (`localhost:5432`, user/jelszó: `se500`).

Példa riport:

```sql
SELECT i.test_time, i.status, p.name, COUNT(*) AS features
FROM inspection i
JOIN product p ON p.id = i.product_id
JOIN measurement m ON m.inspection_id = i.id
WHERE i.serial_code = '288900630038'
  AND i.test_time BETWEEN '2017-06-27 09:00' AND '2017-06-27 11:00'
GROUP BY i.id, i.test_time, i.status, p.name
ORDER BY i.test_time;
```

---

## English

Load SPI result XMLs into a database.
Java 17 + PostgreSQL

A new file lands in a watched folder after every test. About 50 files/hour per machine, 15 machines. Same XML shape, but the number of inspection points changes. Different products per machine (`Panel Name`).

Reports need filters on:

- serial - `Panel/@Code`
- time - `Panel/@TestTime`
- result - `Panel/@Status` (`P` / `F`)

Store Height / Area / Volume (value + limits) keyed by Panel, Image, Location and Feature name.

Samples are under `samples`. One product in the set (`Tls81777`): roughly 4 images, 328 locations, 1184 features, ~1.5 MB each. `…59521099.xml` is a fail, the others pass.

### Schema

15 × 50 × 24 × 365 × 3 is on the order of 20 million tests. At ~1184 features that becomes a very large fact table, so names and limits are not repeated on every row.

- `machine` / `product` - the strings
- `feature_def` - pad identity + limits (written once per product/feature)
- `inspection` - one row per file (serial, time, P/F), indexed for the report
- `measurement` - three `real` values and two ids, PK `(inspection_id, feature_def_id)`

Limits are taken from the first file that sees a given pad. JDBC + StAX, one transaction per file, skip if the filename is already in `inspection`. Broken XML rolls back and the next file still runs.

### Run

```
docker compose up -d
mvn compile exec:java -Dexec.args=samples
```