package edu.berkeley.cs186.database.query;

import edu.berkeley.cs186.database.Database;
import edu.berkeley.cs186.database.DatabaseException;
import edu.berkeley.cs186.database.databox.DataBox;
import edu.berkeley.cs186.database.table.Record;
import edu.berkeley.cs186.database.table.Schema;
import edu.berkeley.cs186.database.common.Pair;
import edu.berkeley.cs186.database.io.Page;

import java.util.*;


public class SortOperator  {
  private Database.Transaction transaction;
  private String tableName;
  private Comparator<Record> comparator;
  private Schema operatorSchema;
  private int numBuffers;

  public SortOperator(Database.Transaction transaction, String tableName, Comparator<Record> comparator) throws DatabaseException, QueryPlanException {
    this.transaction = transaction;
    this.tableName = tableName;
    this.comparator = comparator;
    this.operatorSchema = this.computeSchema();
    this.numBuffers = this.transaction.getNumMemoryPages();
  }

  public Schema computeSchema() throws QueryPlanException {
    try {
      return this.transaction.getFullyQualifiedSchema(this.tableName);
    } catch (DatabaseException de) {
      throw new QueryPlanException(de);
    }
  }


  public class Run {
    String tempTableName;

    public Run() throws DatabaseException {
      this.tempTableName = SortOperator.this.transaction.createTempTable(SortOperator.this.operatorSchema);
    }

    public void addRecord(List<DataBox> values) throws DatabaseException {
      SortOperator.this.transaction.addRecord(this.tempTableName, values);
    }

    public void addRecords(List<Record> records) throws DatabaseException {
      for (Record r: records) {
        this.addRecord(r.getValues());
      }
    }

    public Iterator<Record> iterator() throws DatabaseException {
      return SortOperator.this.transaction.getRecordIterator(this.tempTableName);
    }

    public String tableName() {
      return this.tempTableName;
    }
  }


  /**
   * Returns a NEW run that is the sorted version of the input run.
   * Can do an in memory sort over all the records in this run
   * using one of Java's built-in sorting methods.
   * Note: Don't worry about modifying the original run.
   * Returning a new run would bring one extra page in memory beyond the
   * size of the buffer, but it is done this way for ease.
   */
  public Run sortRun(Run run) throws DatabaseException {
    //throw new UnsupportedOperationException("TODO(hw3): implement");
    List<Record> list = new ArrayList<>();
    Iterator<Record> record = run.iterator();
    while (record.hasNext()) list.add(record.next());

    Collections.sort(list, comparator);
    Run sorted = new Run();
    sorted.addRecords(list);
    return sorted;
  }



  /**
   * Given a list of sorted runs, returns a new run that is the result
   * of merging the input runs. You should use a Priority Queue (java.util.PriorityQueue)
   * to determine which record should be should be added to the output run next.
   * It is recommended that your Priority Queue hold Pair<Record, Integer> objects
   * where a Pair (r, i) is the Record r with the smallest value you are
   * sorting on currently unmerged from run i.
   */
  public Run mergeSortedRuns(List<Run> runs) throws DatabaseException {
    //throw new UnsupportedOperationException("TODO(hw3): implement");
    PriorityQueue<Pair<Record, Integer>> pq = new PriorityQueue<>(new RecordPairComparator());
    List<Iterator<Record>> iters = new ArrayList<>();
    Run merged = new Run();

    for (int i = 0; i < runs.size(); i ++) {
      Iterator<Record> iter = runs.get(i).iterator();
      if (iter.hasNext()) {
        pq.add(new Pair<>(iter.next(), i));
        iters.add(iter);
      }
    }

    while (!pq.isEmpty()) {
      Pair<Record, Integer> top = pq.poll();
      merged.addRecord(top.getFirst().getValues());

      int index = top.getSecond();
      Iterator<Record> nextIter = iters.get(top.getSecond());
      if (nextIter.hasNext()) {
        pq.add(new Pair<>(nextIter.next(), index));
      }
    }

    return merged;
  }

  /**
   * Given a list of N sorted runs, returns a list of
   * sorted runs that is the result of merging (numBuffers - 1)
   * of the input runs at a time.
   */
  public List<Run> mergePass(List<Run> runs) throws DatabaseException {
    //throw new UnsupportedOperationException("TODO(hw3): implement");
    List<Run> list = new ArrayList<>();
    int bulksize = numBuffers - 1;
    int len = runs.size();
    for (int i = 0; i < Math.ceil(len/bulksize); i++) {
      List<Run> bulksorted;
      if ((i + 1) * bulksize <= len) {
        bulksorted = runs.subList(i * bulksize, (i + 1) * bulksize);
      } else {
        bulksorted = runs.subList(i * bulksize, len);
      }
      list.add(mergeSortedRuns(bulksorted));
    }
    return list;
  }


  /**
   * Does an external merge sort on the table with name tableName
   * using numBuffers.
   * Returns the name of the table that backs the final run.
   */
  public String sort() throws DatabaseException {
    // throw new UnsupportedOperationException("TODO(hw3): implement");
    List<Run> list = new ArrayList<>();
    Iterator<Page> pageIterator = this.transaction.getPageIterator(tableName);
    if (pageIterator.hasNext()) pageIterator.next();

    while (pageIterator.hasNext()) {
      Iterator<Record> recordIterator = this.transaction.getBlockIterator(tableName, pageIterator, numBuffers);
      Run run = new Run();
      List<Record> temp = new ArrayList<>();

      while (recordIterator.hasNext()) {
        temp.add(recordIterator.next());
      }
      run.addRecords(temp);
      list.add(sortRun(run));
    }

    while (list.size() > 1) {
      list = mergePass(list);
    }
    return list.get(0).tableName();
  }

  private class RecordPairComparator implements Comparator<Pair<Record, Integer>> {
    public int compare(Pair<Record, Integer> o1, Pair<Record, Integer> o2) {
      return SortOperator.this.comparator.compare(o1.getFirst(), o2.getFirst());

    }
  }
  public Run createRun() throws DatabaseException {
    return new Run();
  }
}

