require "lib/types/table/table"
require "test/unit"
require "rubygems"

class TestCallback < Test::Unit::TestCase
  def default_test
    t = Table.new('Orli', Table::INFINITY, Key.new(0), [Integer, String])
    
    c = Callback.new(t)
    
    assert_raise(RuntimeError){c.insertion(nil)}
    assert_raise(RuntimeError){c.deletion(nil)}
  end
end