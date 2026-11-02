ALTER TABLE positions
    ALTER COLUMN quantity TYPE NUMERIC(24, 10) USING ROUND(quantity::numeric, 10),
    ALTER COLUMN average_price_paid TYPE NUMERIC(24, 10) USING ROUND(average_price_paid::numeric, 10),
    ALTER COLUMN current_price TYPE NUMERIC(24, 10) USING ROUND(current_price::numeric, 10),
    ALTER COLUMN current_value TYPE NUMERIC(19, 4) USING ROUND(current_value::numeric, 4),
    ALTER COLUMN unrealized_profit_loss TYPE NUMERIC(19, 4) USING ROUND(unrealized_profit_loss::numeric, 4),
    ALTER COLUMN fx_impact TYPE NUMERIC(19, 4) USING ROUND(fx_impact::numeric, 4),
    ALTER COLUMN total_cost TYPE NUMERIC(19, 4) USING ROUND(total_cost::numeric, 4);
