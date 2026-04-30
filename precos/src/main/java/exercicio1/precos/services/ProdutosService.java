package exercicio1.precos.services;

import lombok.Getter;

@Getter
public class ProdutosService {
    private final Product products;
    private final int quantity;

    public ProdutosService(Product products,int quantity){
        this.products = products;
        this.quantity = quantity;
    }

    public double makeDiscount(){
        if (this.quantity >=10){
            return 1.05;
        }
        else{
            return 1.0;
        }
    }


    public double totalPrice(){
        return this.products.getPreco()*this.quantity*makeDiscount();
    }




}
