package exercicio1.precos.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import exercicio1.precos.dto.CompraDTO;
import exercicio1.precos.services.Product;
import exercicio1.precos.services.ProdutosService;


@RestController
@RequestMapping("/compra")
public class ProdutoController {


    @GetMapping()
    public ResponseEntity<CompraDTO> getMethodName(@RequestParam String nome,
        @RequestParam int quantidade
    ) {
        Product product;
        switch (nome) {
            case "frango":
                    product = Product.FRANGO;
                break;
            case "peru":
                    product = Product.PERU;
                break;
            case "chester":
                    product = Product.CHESTER;
                break;
            default:
                throw new AssertionError("Nome do produto errado");
            }
            
        ProdutosService produtosService = new ProdutosService(product, quantidade);
        
        boolean desconto = produtosService.makeDiscount();
        double preco = product.getPreco();
        double total = produtosService.totalPrice();

        CompraDTO compraDTO = new CompraDTO(nome,quantidade,preco,desconto,total);

        return ResponseEntity.ok(compraDTO);
        

    }
    
    
}
