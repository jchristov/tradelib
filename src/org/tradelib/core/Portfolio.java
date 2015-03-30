package org.tradelib.core;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

public class Portfolio {
   public static final String DEFAULT_NAME = "default";
   
   private static final Logger logger = Logger.getLogger(Portfolio.class.getName());

   private class Transaction {
      public LocalDateTime ts;
      public long quantity;
      public double price;
      public double value;
      public double averageCost;
      public long positionQuantity;
      public double positionAverageCost;
      public double grossPnl;
      public double netPnl;
      public double fees;

      public Transaction(LocalDateTime ldt, long q, double p, double f) {
         ts = ldt;
         quantity = q;
         price = p;
         fees = f;
         value = 0.0;
         averageCost = 0.0;
         positionQuantity = 0;
         positionAverageCost = 0.0;
         grossPnl = 0.0;
         netPnl = 0.0;
      }

      public Transaction(LocalDateTime ldt) {
         ts = ldt;
         quantity = 0;
         price = 0.0;
         fees = 0.0;
         value = 0.0;
         averageCost = 0.0;
         positionQuantity = 0;
         positionAverageCost = 0.0;
         grossPnl = 0.0;
         netPnl = 0.0;
      }
   }
   
   private class PositionPnl {
      public LocalDateTime ts;
      public double positionQuantity;
      public double positionValue;
      public double positionAverageCost;
      public double transactionValue;
      public double realizedPnl;
      public double unrealizedPnl;
      public double grossPnl;
      public double netPnl;
      public double fees;
      
      public PositionPnl(LocalDateTime ldt) {
         positionQuantity = 0.0;
         positionValue = 0.0;
         positionAverageCost = 0.0;
         transactionValue = 0.0;
         realizedPnl = 0.0;
         unrealizedPnl = 0.0;
         grossPnl = 0.0;
         netPnl = 0.0;
         fees = 0.0;
         
         ts = ldt;
      }
      
      public PositionPnl() {
         this(LocalDateTime.MIN);
      }
   }
   
   // 'Long.Value', 'Short.Value', 'Net.Value', 'Gross.Value', 'Period.Realized.PL', 'Period.Unrealized.PL', 'Gross.Trading.PL', 'Txn.Fees', 'Net.Trading.PL'
   private class Summary {
      public LocalDateTime ts; // the timestamp for this summary
      public double longValue;
      public double shortValue;
      public double netValue;
      public double grossValue;
      public double txnFees;
      public double realizedPnl; // period realized PnL
      public double unrealizedPnl; // period unrealized PnL
      public double grossPnl; // gross trading PnL
      public double netPnl; // net trading PnL
   }
   
   private class InstrumentCB {
      public ArrayList<Transaction> transactions;
      public ArrayList<PositionPnl> positionPnls;
      public ArrayList<Summary> summaries;
      
      public InstrumentCB() {
         transactions = new ArrayList<Transaction>();
         transactions.add(new Transaction(LocalDateTime.MIN));
         
         positionPnls = new ArrayList<Portfolio.PositionPnl>();
         positionPnls.add(new PositionPnl());
         
         summaries = new ArrayList<Summary>();
      }
   }
   
   String name_;
   HashMap<String, InstrumentCB> instrumentMap;
   
   public Portfolio(String name) {
      this.name_ = name;
      this.instrumentMap = new HashMap<String, InstrumentCB>();
   }
   
   public Portfolio() {
      this.name_ = DEFAULT_NAME;
      this.instrumentMap = new HashMap<String, InstrumentCB>();
   }
   
   public void addInstrument(Instrument i) {
      InstrumentCB icb = instrumentMap.get(i.getSymbol());
      if(icb == null) {
         instrumentMap.put(i.getSymbol(), new InstrumentCB());
      }
   }
   
   public void addTransaction(Instrument i, LocalDateTime ldt, long q, double p, double f) {
      InstrumentCB icb = instrumentMap.get(i.getSymbol());
      if(icb == null) {
         icb = new InstrumentCB();
         instrumentMap.put(i.getSymbol(), icb);
      }
      
      ArrayList<Transaction> instrumentTransactions = icb.transactions;
      
      assert ldt.isAfter(instrumentTransactions.get(instrumentTransactions.size()-1).ts) : "Transactions must be added in chronological order!";
      
      Transaction transaction = new Transaction(ldt, q, p, f);
      long ppq = instrumentTransactions.get(instrumentTransactions.size()-1).positionQuantity;
      if(ppq != 0 && ppq != -transaction.quantity && Long.signum(ppq + transaction.quantity) != Long.signum(ppq)) {
         // Split the transaction into two, first add the zero-ing transaction
         double perUnitFee = transaction.fees / Math.abs(transaction.quantity);
         addTransaction(i, transaction.ts, -ppq, transaction.price, perUnitFee * Math.abs(ppq));

         // Adjust the inputs to reflect what's left to transact, increase the 
         // date time by a bit to keep the uniqueness in the transaction set.
         transaction.ts = transaction.ts.plusNanos(1);
         transaction.quantity += ppq;
         ppq = 0;
         transaction.fees = perUnitFee * Math.abs(transaction.quantity); 
      }
      
      // Transaction value, gross of fees
      transaction.value = transaction.quantity * transaction.price * i.getBpv();

      // Transaction average cost
      transaction.averageCost = transaction.value / (transaction.quantity * i.getBpv());

      // Calculate the new quantity for this position
      transaction.positionQuantity = ppq + transaction.quantity;

      // Previous position average cost
      double ppac = instrumentTransactions.get(instrumentTransactions.size()-1).positionAverageCost;

      // Calculate position average cost
      if (transaction.positionQuantity == 0) {
         transaction.positionAverageCost = 0.0;
      } else if(Math.abs(ppq) > Math.abs(transaction.positionQuantity)) {
         transaction.positionAverageCost = ppac;
      } else {
         transaction.positionAverageCost = (ppq * ppac * i.getBpv() + transaction.value) /
                        (transaction.positionQuantity * i.getBpv());
      }

      // Calculate PnL
      if(Math.abs(ppq) < Math.abs(transaction.positionQuantity) || ppq == 0) {
         transaction.grossPnl = 0.0;
      } else {
         transaction.grossPnl = transaction.quantity * i.getBpv() * (ppac - transaction.averageCost); 
      }

      transaction.netPnl = transaction.grossPnl + transaction.fees;
      
//      logger_.info("Portfolio: adding transaction at " + transaction.ts.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) +
//            ", previous transaction at " + transactions.get(transactions.size()-1).ts.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
      assert ldt.isAfter(instrumentTransactions.get(instrumentTransactions.size()-1).ts) : "Transactions must be added in chronological order!";
      instrumentTransactions.add(transaction);
   }
   
   public void addTransaction(Execution e) {
      addTransaction(e.getInstrument(), e.getDateTime(), e.getQuantity(), e.getPrice(), e.getFees());
   }
   
   public void updatePnl(Instrument instrument, TimeSeries<Double> prices) {
      InstrumentCB icb = instrumentMap.get(instrument.getSymbol());
      List<Transaction> transactions = icb.transactions;
      
      PositionPnl lastPnl = icb.positionPnls.get(icb.positionPnls.size() - 1);
      
      // We only process prices after the last PnL
      int priceId = 0;
      while(priceId < prices.size() && prices.getTimestamp(priceId).isBefore(lastPnl.ts)) {
         ++priceId;
      }
      
      // Find qualifying transactions
      int txnId = 0;
      while(txnId < transactions.size() && !transactions.get(txnId).ts.isAfter(lastPnl.ts)) {
         ++txnId;
      }
      
      boolean hasPrices = priceId < prices.size();
      boolean hasTxns = txnId < transactions.size();
      
      while(hasPrices || hasTxns) {
         if(hasPrices && hasTxns && prices.getTimestamp(priceId).equals(transactions.get(txnId).ts)) {
            Transaction txn = transactions.get(txnId);
            
            PositionPnl ppnl = new PositionPnl(txn.ts);
            
            // The current time is both in the price list and in the transaction list
            ppnl.positionQuantity = txn.positionQuantity;
            ppnl.positionAverageCost = txn.positionAverageCost;
            ppnl.transactionValue = txn.value;
            ppnl.fees = txn.fees;
            ppnl.positionValue = instrument.getBpv() * ppnl.positionQuantity * prices.get(priceId);
            ppnl.grossPnl = ppnl.positionValue - lastPnl.positionValue - ppnl.transactionValue;
            ppnl.realizedPnl = txn.grossPnl;
            ppnl.unrealizedPnl = ppnl.grossPnl - txn.grossPnl;
            ppnl.netPnl = ppnl.grossPnl + txn.fees;

            icb.positionPnls.add(ppnl);
            lastPnl = ppnl;
            
            ++priceId;
            ++txnId;
            
            hasPrices = priceId < prices.size();
            hasTxns = txnId < transactions.size();
         } else if(!hasTxns || prices.getTimestamp(priceId).isBefore(transactions.get(txnId).ts)) {
            PositionPnl ppnl = new PositionPnl(prices.getTimestamp(priceId));
            
            // No position change, only mark for the price
            ppnl.positionQuantity = lastPnl.positionQuantity;
            ppnl.positionAverageCost = lastPnl.positionAverageCost;
            ppnl.transactionValue = 0.0;
            ppnl.fees = 0.0;
            ppnl.positionValue = instrument.getBpv() * ppnl.positionQuantity * prices.get(priceId);
            ppnl.grossPnl = ppnl.positionValue - lastPnl.positionValue;
            ppnl.realizedPnl = 0.0;
            ppnl.unrealizedPnl = ppnl.grossPnl;
            ppnl.netPnl = ppnl.grossPnl;

            icb.positionPnls.add(ppnl);
            lastPnl = ppnl;
            
            ++priceId;
            hasPrices = priceId < prices.size();
         } else {
            Transaction txn = transactions.get(txnId);
            
            PositionPnl ppnl = new PositionPnl(transactions.get(txnId).ts);
            
            ppnl.positionQuantity = txn.positionQuantity;
            ppnl.positionAverageCost = txn.positionAverageCost;
            ppnl.transactionValue = txn.value;
            ppnl.fees = txn.fees;
            // Use the previous price
            ppnl.positionValue = instrument.getBpv() * ppnl.positionQuantity * prices.get(priceId - 1);
            ppnl.grossPnl = ppnl.positionValue - lastPnl.positionValue - ppnl.transactionValue;
            ppnl.realizedPnl = txn.grossPnl;
            ppnl.unrealizedPnl = ppnl.grossPnl - txn.grossPnl;
            ppnl.netPnl = ppnl.grossPnl + txn.fees;

            icb.positionPnls.add(ppnl);
            lastPnl = ppnl;
            
            ++txnId;
            hasTxns = txnId < transactions.size();
         }
      }
   }
   
   /**
    * @brief Computes the PnL for an instrument
    *
    * Computes the PnL for the specified instrument using the specified prices.
    *
    * @param[in] instrument - the instrument
    * @param[in] prices - the price series for the PnL computation
    */
   TimeSeries<Double> getPnl(Instrument instrument, TimeSeries<Double> prices)
   {
      TimeSeries<Double> pnl = new TimeSeries<Double>();
      
      ArrayList<Transaction> transactions = instrumentMap.get(instrument.getSymbol()).transactions;

      // Handle the trivial case of no transactions.
      if(transactions.size() <= 1) {
         pnl.addAll(prices.getTimestamps(), 0.0);
         return pnl;
      }

      // Find the start
      int currentTransaction = 1;
      int ii = 0;
      while(ii < prices.size() && prices.getTimestamp(ii).isBefore(transactions.get(currentTransaction).ts)) {
         ++ii;
      }

      // Set the pnl to 0 from beginning of time to the first transaction
      pnl.addAll(prices.getTimestamps(ii), 0.0);
      if(ii == prices.size()) return pnl;

      double previousPositionValue = 0.0;
      while(ii < prices.size() && currentTransaction < transactions.size()) {
         Transaction transaction = transactions.get(currentTransaction);
//         System.out.println("> " + prices.getTimestamp(ii).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + " " + prices.getTimestamp(ii).getNano() + " nanos, " + Integer.toString(ii));
//         System.out.println(transaction.ts.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + " " + 
//               transaction.ts.getNano() + " nanos, id=" + Integer.toString(currentTransaction) + ", quantity = " +
//               transaction.quantity);
         if(prices.getTimestamp(ii).equals(transaction.ts)) {
            // The current time is both in the price list and in the transaction list
            double transactionValue = transaction.value;
            double positionValue = instrument.getBpv() * transaction.positionQuantity * prices.get(ii);
            double pnlValue = positionValue - previousPositionValue - transactionValue + transaction.fees; 
            pnl.add(prices.getTimestamp(ii), pnlValue);

            ++ii;
            ++currentTransaction;

            previousPositionValue = positionValue;
         } else if(prices.getTimestamp(ii).isBefore(transaction.ts)) {
            // Only in the price list - use the previous position
            double positionValue = transactions.get(currentTransaction-1).positionQuantity * instrument.getBpv() * prices.get(ii).doubleValue();
            pnl.add(prices.getTimestamp(ii), positionValue - previousPositionValue);

            ++ii;

            previousPositionValue = positionValue;
         } else {
            if(ii > 0) {
               // Only in the transaction list - use the previous price
               double positionValue = transaction.positionQuantity * instrument.getBpv() * prices.get(ii-1);
               double pnlValue = positionValue - previousPositionValue - transaction.value;
               pnl.add(transaction.ts, pnlValue);

               previousPositionValue = positionValue;
            } else {
               // No "previous" price - no pnl
               pnl.add(transaction.ts, 0.0);
            }

            ++currentTransaction;
         }
      }

      while(ii < prices.size()) {
         double positionValue = transactions.get(currentTransaction - 1).positionQuantity * instrument.getBpv() * prices.get(ii).doubleValue();
         pnl.add(prices.getTimestamp(ii), positionValue - previousPositionValue);

         ++ii;

         previousPositionValue = positionValue;
      }
      
      return pnl;
   }
   
   /**
    * @brief Computes the PnL for the current position
    *
    * Computes the PnL for the current position and the specified price. Undefined
    * behavior if a position doesn't exist.
    * 
    * A position starts with the first transaction which sets a quantity different than 0.
    *
    * Uses the gross PnL for all transactions part of this trade.
    *
    * @param[in] instrument the instrument
    * @param[in] price the price to compute the PnL
    */
   public Pnl getPositionPnl(Instrument instrument, Double price) {
      ArrayList<Transaction> instrumentTransactions = instrumentMap.get(instrument.getSymbol()).transactions;
      int ii = instrumentTransactions.size() - 1;
      Transaction transaction = instrumentTransactions.get(ii);
      assert transaction.positionQuantity != 0 : "Must not be called without a position";
      
      Pnl pnl = new Pnl();
      pnl.unrealized = instrument.getBpv() * transaction.positionQuantity * (price.doubleValue() - transaction.positionAverageCost);
      // Add all realized pnl
      while(transaction.positionQuantity != 0) {
         pnl.realized += transaction.grossPnl;
         --ii;
         transaction = instrumentTransactions.get(ii);
      }
      
      return pnl;
   }
   
   
   /**
    * @brief Closes the position for a given instrument.
    * 
    * Adds a transaction to close the position for an instrument. This is useful
    * at the end of a backtest, in order to include an open position into the
    * PnL computations.
    * 
    * @param instrument The instrument.
    * @param ldt The timestamp to use to close the position.
    * @param price The transaction price.
    * @param fees The transaction fees.
    */
   public void closePosition(Instrument instrument, LocalDateTime ldt, double price, double fees) {
      InstrumentCB icb = instrumentMap.get(instrument.getSymbol());
      if(icb == null) return;
      ArrayList<Transaction> instrumentTransactions = icb.transactions;
      if(instrumentTransactions == null || instrumentTransactions.size() <= 1) return;
      Transaction transaction = instrumentTransactions.get(instrumentTransactions.size()-1);
      if(transaction.quantity != 0) addTransaction(instrument, ldt, -transaction.quantity, price, fees);
   }
   
   public class TradingResults {
      public TimeSeries<Double> pnl;
      public List<Trade> stats;
      public TradeSummary all;
      public TradeSummary longs;
      public TradeSummary shorts;
      
      public TradingResults() {
         pnl = new TimeSeries<Double>();
         stats = new ArrayList<Trade>();
         all = new TradeSummary();
         longs = new TradeSummary();
         shorts = new TradeSummary();
      }
   }
   
   public class TradeSummaryBuilder {
      private long numTrades;

      private double grossProfits;
      private double grossLosses;

      private AverageAndVariance dailyPnlStats;
      private AverageAndVariance pnlStats;

      private long nonZero;
      private long positive;
      private long negative;

      private double maxWin;
      private double maxLoss;

      private Average averageWinTrade;
      private Average averageLossTrade;

      private TimeSeries<Double> pnl;
      private int pnlId;

      private double equity;
      private double minEquity;
      private double maxEquity;
      private double maxDrawdown;
      
      public TradeSummaryBuilder(TimeSeries<Double> pnl) {
         numTrades = 0;
         
         grossProfits = 0.0;
         grossLosses = 0.0;
         
         dailyPnlStats = new AverageAndVariance();
         pnlStats = new AverageAndVariance();
         
         nonZero = 0; positive = 0; negative = 0;
         
         maxWin = 0.0;
         maxLoss = 0.0;
         
         averageWinTrade = new Average();
         averageLossTrade = new Average();
         
         this.pnl = new TimeSeries<Double>(pnl);
         
         pnlId = 0;
         
         equity = 0.0;
         minEquity = Double.MAX_VALUE;
         maxEquity = Double.MIN_VALUE;
         maxDrawdown = Double.MAX_VALUE;
      }
      
      public void add(Trade ts) {
         ++numTrades;
         if(ts.pnl < 0.0) {
            ++nonZero;
            ++negative;
            averageLossTrade.add(ts.pnl);
            grossLosses += ts.pnl;
         } else if(ts.pnl > 0.0) {
            ++nonZero;
            ++positive;
            averageWinTrade.add(ts.pnl);
            grossProfits += ts.pnl;
         }

         pnlStats.add(ts.pnl);

         maxWin = Math.max(maxWin, ts.pnl);
         maxLoss = Math.min(maxLoss, ts.pnl);

         // Set the PnL to zero until the current trade begins
         while(pnlId < pnl.size() && pnl.getTimestamp(pnlId).isBefore(ts.start)) {
            pnl.set(pnlId, 0.0);
            ++pnlId;
         }

         // Keep the PnL as it is inside the current trade
         while(pnlId < pnl.size() && !pnl.getTimestamp(pnlId).isAfter(ts.end)) {
            equity += pnl.get(pnlId); 
            maxEquity = Math.max(maxEquity, equity);
            minEquity = Math.min(minEquity, equity);
            maxDrawdown = Math.min(maxDrawdown, equity - maxEquity);

            if(pnl.get(pnlId) != 0.0) {
               dailyPnlStats.add(pnl.get(pnlId));
            }

            ++pnlId;
         }
      }
      
      public TradeSummary summarize() {
         TradeSummary summary = new TradeSummary();
         summary.numTrades = numTrades;

         if(numTrades > 0) {
            summary.grossLosses = grossLosses;
            summary.grossProfits = grossProfits;
            
            if(grossLosses != 0.0) {
               summary.profitFactor = Math.abs(grossProfits/grossLosses);
            } else {
               summary.profitFactor = Math.abs(grossProfits);
            }

            summary.averageTradePnl = pnlStats.getAverage();
            summary.tradePnlStdDev = pnlStats.getStdDev();
            if(numTrades > 0) {
               summary.pctNegative = (double)negative/numTrades*100.0; 
               summary.pctPositive = (double)positive/numTrades*100.0; 
            } else {
               summary.pctNegative = 0.0;
               summary.pctPositive = 0.0;
            }

            summary.maxLoss = maxLoss;
            summary.maxWin = maxWin;
            summary.averageLoss = averageLossTrade.get();
            summary.averageWin = averageWinTrade.get();
            
            if(summary.averageLoss != 0.0) {
               summary.averageWinLoss = summary.averageWin/(summary.averageLoss*(-1.0)); 
            } else {
               summary.averageWinLoss = summary.averageWin;
            }

            summary.equityMin = minEquity;
            summary.equityMax = maxEquity;
            summary.maxDrawdown = maxDrawdown;

            summary.averageDailyPnl = dailyPnlStats.getAverage();
            summary.dailyPnlStdDev = dailyPnlStats.getStdDev();
            summary.sharpeRatio = Functions.sharpeRatio(summary.averageDailyPnl, summary.dailyPnlStdDev, 252); 
         }
         
         return summary;
      }
   }
   
   /**
    * @brief Computes statistics for each trade.
    *
    * @param[in] instrument the instrument
    */
   public List<Trade> getTrades(Instrument instrument) {
      List<Trade> list = new ArrayList<Trade>();

      InstrumentCB icb = instrumentMap.get(instrument.getSymbol());
      if(icb == null) return list;
      
      ArrayList<Transaction> transactions = icb.transactions;
      if(transactions == null) return list;
      
      int ii = 0;
      // Position at the first non-zero quantity
      while(true) {
         if(ii == transactions.size()) return list; // No meaningful transactions
         if(transactions.get(ii).positionQuantity != 0) break; // Found a real transaction
         ++ii;
      }
      int jj = ii;
      for(++jj; jj < transactions.size() && transactions.get(jj).positionQuantity != 0; ++jj) {
      }
      if(jj != transactions.size()) ++jj;

      // [ii, jj) contains all transactions participating in the current trade
      while(true) {
         double positionCostBasis = 0.0;
         
         Trade ts = new Trade();

         int kk = jj - 1;
         
         ts.start = transactions.get(ii).ts; 
         ts.end = transactions.get(kk).ts;
         ts.initialPosition = transactions.get(ii).quantity;
         ts.maxPosition = 0;
         ts.numTransactions = 0;
         ts.maxNotionalCost = 0.0;
         ts.fees = 0.0;

         for(int ll = ii; ll < jj; ++ll) {
            Transaction transaction = transactions.get(ll);
            if(transaction.value != 0.0) ++ts.numTransactions;

            positionCostBasis = positionCostBasis + transaction.value;
            ts.fees += transaction.fees;

            if(Math.abs(transaction.positionQuantity) > Math.abs(ts.maxPosition)) {
               ts.maxPosition = transaction.positionQuantity;
               ts.maxNotionalCost = positionCostBasis;
            }
         }

         double positionValue =  transactions.get(kk).positionQuantity * instrument.getBpv() * transactions.get(kk).price;
         ts.pnl = positionValue - positionCostBasis;
         ts.pctPnl = ts.pnl/Math.abs(ts.maxNotionalCost); 
         ts.tickPnl = 0.0;
         
         list.add(ts);

         // Advance to the next trade
         if(jj == transactions.size()) break;
         ii = jj;
         for(++jj; jj < transactions.size() && transactions.get(jj).positionQuantity != 0; ++jj) {
         }
         if(jj != transactions.size()) ++jj;
      }
      return list;
   }
   
   public TradingResults getTradingResults(Instrument instrument, TimeSeries<Double> prices) {
      TradingResults tr = new TradingResults();
      tr.pnl = getPnl(instrument, prices);
      tr.stats = getTrades(instrument);
      TradeSummaryBuilder shorts = new TradeSummaryBuilder(tr.pnl);
      TradeSummaryBuilder longs = new TradeSummaryBuilder(tr.pnl);
      TradeSummaryBuilder all = new TradeSummaryBuilder(tr.pnl);
      
      for(Trade ts : tr.stats) {
         if(ts.initialPosition > 0) {
            all.add(ts);
            longs.add(ts);
         } else if(ts.initialPosition < 0) {
            all.add(ts);
            shorts.add(ts);
         }
      }
      tr.all = all.summarize();
      tr.longs = longs.summarize();
      tr.shorts = shorts.summarize();
      return tr;
   }
   
   public Iterable<String> symbols() {
      return instrumentMap.keySet();
   }
}
