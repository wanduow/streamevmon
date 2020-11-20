package nz.net.wand.streamevmon.events.grouping.graph

import java.util.function.Supplier

import org.jgrapht.graph.DefaultWeightedEdge

class DefaultWeightedEdgeSupplier extends Supplier[DefaultWeightedEdge] {
  override def get(): DefaultWeightedEdge = new DefaultWeightedEdge()
}
