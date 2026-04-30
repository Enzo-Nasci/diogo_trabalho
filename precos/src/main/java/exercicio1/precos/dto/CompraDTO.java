package exercicio1.precos.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CompraDTO {
    private String produto;
    private int quantidade;
    private double preco;
    private boolean desconto;
    private double total;
    
    
}
